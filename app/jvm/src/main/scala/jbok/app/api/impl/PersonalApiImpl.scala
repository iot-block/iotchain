package jbok.app.api.impl

import cats.effect.IO
import cats.implicits._
import cats.effect.concurrent.Ref
import jbok.core.config.Configs.HistoryConfig
import jbok.core.keystore.{KeyStorePlatform, Wallet}
import jbok.core.ledger.History
import jbok.core.models.{Address, SignedTransaction}
import jbok.core.pool.TxPool
import jbok.crypto._
import jbok.crypto.signature._
import jbok.network.rpc.jsonrpc.RpcErrors
import jbok.sdk.api.{BlockParam, PersonalAPI, TransactionRequest}
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.util.Try

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.EitherProjectionPartial"))
object PersonalApiImpl {
  def apply(
      keyStore: KeyStorePlatform[IO],
      history: History[IO],
      blockChainConfig: HistoryConfig,
      txPool: TxPool[IO]
  ): IO[PersonalAPI[IO]] =
    for {
      unlockedWallets <- Ref.of[IO, Map[Address, Wallet]](Map.empty)
    } yield
      new PersonalAPI[IO] {
        override def importRawKey(privateKey: ByteVector, passphrase: String): IO[Address] =
          keyStore.importPrivateKey(privateKey, passphrase)

        override def newAccount(passphrase: String): IO[Address] =
          keyStore.newAccount(passphrase)

        override def delAccount(address: Address): IO[Boolean] =
          keyStore.deleteAccount(address)

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
            sig <- Signature[ECDSA].sign[IO](getMessageToSign(message).toArray, wallet.keyPair, history.chainId)
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
                  IO.raiseError(RpcErrors.invalidRequest)
              }
          }

        override def deleteWallet(address: Address): IO[Boolean] =
          for {
            _ <- unlockedWallets.update(_ - address)
            r <- keyStore.deleteAccount(address)
          } yield r

        override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): IO[Boolean] =
          keyStore.changePassphrase(address, oldPassphrase, newPassphrase)

        override def getAccountTransactions(address: Address,
                                            fromBlock: BlockParam,
                                            toBlock: BlockParam): IO[List[SignedTransaction]] = {
          def collectTxs: PartialFunction[SignedTransaction, SignedTransaction] = {
            case stx if stx.senderAddress.nonEmpty && stx.senderAddress.get == address => stx
            case stx if stx.receivingAddress == address                                => stx
          }

          val bestNumber = history.getBestBlockNumber.unsafeRunSync()

          def resolveNumber(param: BlockParam): BigInt = param match {
            case BlockParam.Earliest      => 0
            case BlockParam.Latest        => bestNumber
            case BlockParam.WithNumber(n) => n
          }

          val sn = resolveNumber(fromBlock)
          val en = resolveNumber(toBlock)

          for {
            blocks <- (sn to en).toList.filter(_ >= BigInt(0)).traverse(history.getBlockByNumber)
            stxsFromBlock = blocks.collect {
              case Some(block) => block.body.transactionList.collect(collectTxs)
            }.flatten
            pendingStxs <- txPool.getPendingTransactions
            stxsFromPool = pendingStxs.keys.toList.collect(collectTxs)
          } yield stxsFromBlock ++ stxsFromPool
        }

        private[jbok] def getMessageToSign(message: ByteVector) = {
          val prefixed: Array[Byte] =
            0x19.toByte +: s"Ethereum Signed Message:\n${message.length}".getBytes ++: message.toArray
          ByteVector(prefixed.kec256)
        }

        private[jbok] def sendTransaction(request: TransactionRequest, wallet: Wallet): IO[ByteVector] = {
          implicit val chainId: BigInt = history.chainId
          for {
            pending <- txPool.getPendingTransactions
            latestNonceOpt = Try(pending.collect {
              case (stx, _) if stx.senderAddress.get == wallet.address => stx.nonce
            }.max).toOption
            bn              <- history.getBestBlockNumber
            currentNonceOpt <- history.getAccount(request.from, bn).map(_.map(_.nonce.toBigInt))
            maybeNextTxNonce = latestNonceOpt.map(_ + 1).orElse(currentNonceOpt)
            tx               = request.toTransaction(maybeNextTxNonce.getOrElse(blockChainConfig.accountStartNonce))

            stx <- wallet.signTx[IO](tx)
            _   <- txPool.addOrUpdateTransaction(stx)
          } yield stx.hash
        }
      }
}
