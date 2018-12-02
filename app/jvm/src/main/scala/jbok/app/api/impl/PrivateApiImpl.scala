package jbok.app.api.impl

import cats.effect.IO
import cats.effect.concurrent.Ref
import jbok.app.api.{PrivateAPI, TransactionRequest}
import jbok.core.config.Configs.BlockChainConfig
import jbok.core.keystore.{KeyStorePlatform, Wallet}
import jbok.core.ledger.History
import jbok.core.models.Address
import jbok.core.pool.TxPool
import jbok.crypto._
import jbok.crypto.signature._
import jbok.network.json.JsonRpcErrors
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.util.Try

object PrivateApiImpl {
  def apply(
      keyStore: KeyStorePlatform[IO],
      history: History[IO],
      blockChainConfig: BlockChainConfig,
      txPool: TxPool[IO],
  ): IO[PrivateAPI] =
    for {
      unlockedWallets <- Ref.of[IO, Map[Address, Wallet]](Map.empty)
    } yield
      new PrivateAPI {
        override def importRawKey(privateKey: ByteVector, passphrase: String): IO[Address] =
          keyStore.importPrivateKey(privateKey, passphrase)

        override def newAccount(passphrase: String): IO[Address] =
          keyStore.newAccount(passphrase)

        override def delAccount(address: Address): IO[Boolean] =
          keyStore.deleteWallet(address)

        override def listAccounts: IO[List[Address]] =
          keyStore.listAccounts

        override def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): IO[Boolean] =
          for {
            wallet <- keyStore.unlockAccount(address, passphrase)
            _      <- unlockedWallets.update(_ + (address -> wallet))
          } yield true

        override def lockAccount(address: Address): IO[Boolean] =
          unlockedWallets.update(_ - address).map(_ => true)

        override def sign(message: ByteVector, address: Address, passphrase: Option[String]): IO[CryptoSignature] =
          for {
            wallet <- if (passphrase.isDefined) {
              keyStore.unlockAccount(address, passphrase.get)
            } else {
              unlockedWallets.get.map(_(address))
            }
            sig <- Signature[ECDSA].sign(getMessageToSign(message).toArray, wallet.keyPair, history.chainId)
          } yield sig

        override def ecRecover(message: ByteVector, signature: CryptoSignature): IO[Address] =
          IO {
            Signature[ECDSA]
              .recoverPublic(getMessageToSign(message).toArray, signature, history.chainId)
              .map(public => Address(public.bytes.kec256))
              .get
          }

        override def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): IO[ByteVector] =
          passphrase match {
            case Some(p) =>
              for {
                wallet <- keyStore.unlockAccount(tx.from, p)
                hash   <- sendTransaction(tx, wallet)
              } yield hash

            case None =>
              unlockedWallets.get.map(_.get(tx.from)).flatMap {
                case Some(wallet) =>
                  sendTransaction(tx, wallet)
                case None =>
                  IO.raiseError(JsonRpcErrors.invalidRequest)
              }
          }

        override def deleteWallet(address: Address): IO[Boolean] =
          for {
            _ <- unlockedWallets.update(_ - address)
            r <- keyStore.deleteWallet(address)
          } yield r

        override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): IO[Boolean] =
          keyStore.changePassphrase(address, oldPassphrase, newPassphrase)

        private[jbok] def getMessageToSign(message: ByteVector) = {
          val prefixed: Array[Byte] =
            0x19.toByte +: s"Ethereum Signed Message:\n${message.length}".getBytes ++: message.toArray
          ByteVector(prefixed.kec256)
        }

        private[jbok] def sendTransaction(request: TransactionRequest, wallet: Wallet): IO[ByteVector] =
          for {
            pending <- txPool.getPendingTransactions
            latestNonceOpt = Try(pending.collect {
              case (stx, _) if stx.senderAddress.get == wallet.address => stx.nonce
            }.max).toOption
            bn              <- history.getBestBlockNumber
            currentNonceOpt <- history.getAccount(request.from, bn).map(_.map(_.nonce.toBigInt))
            maybeNextTxNonce = latestNonceOpt.map(_ + 1).orElse(currentNonceOpt)
            tx               = request.toTransaction(maybeNextTxNonce.getOrElse(blockChainConfig.accountStartNonce))
            stx              = wallet.signTx(tx, blockChainConfig.chainId)
            _ <- txPool.addOrUpdateTransaction(stx)
          } yield stx.hash
      }
}
