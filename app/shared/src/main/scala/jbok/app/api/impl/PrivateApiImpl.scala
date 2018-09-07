package jbok.app.api.impl

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import fs2.async.Ref
import jbok.app.api.{PrivateAPI, TransactionRequest}
import jbok.core.Configs.BlockChainConfig
import jbok.core.keystore.{KeyStore, Wallet}
import jbok.core.models.Address
import jbok.core.History
import jbok.core.pool.TxPool
import jbok.crypto._
import jbok.crypto.signature.CryptoSignature
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.network.json.JsonRPCError
import jbok.network.json.JsonRPCResponse._
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.util.Try

class PrivateApiImpl(
    keyStore: KeyStore[IO],
    history: History[IO],
    blockChainConfig: BlockChainConfig,
    txPool: TxPool[IO],
    unlockedWallets: Ref[IO, Map[Address, Wallet]]
) extends PrivateAPI {
  override def importRawKey(privateKey: ByteVector, passphrase: String): Response[Address] =
    keyStore.importPrivateKey(privateKey, passphrase).map(_.leftMap(e => invalidRequest(e.toString)))

  override def newAccount(passphrase: String): Response[Address] =
    keyStore.newAccount(passphrase).map(_.leftMap(e => invalidRequest(e.toString)))

  override def delAccount(address: Address): Response[Boolean] =
    keyStore.deleteWallet(address).map(_.leftMap(e => invalidRequest(e.toString)))

  override def listAccounts: Response[List[Address]] =
    keyStore.listAccounts.map(_.leftMap(e => invalidRequest(e.toString)))

  override def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): Response[Boolean] =
    EitherT(keyStore.unlockAccount(address, passphrase))
      .leftMap(e => invalidRequest(e.toString))
      .semiflatMap(wallet => unlockedWallets.modify(_ + (address -> wallet)).map(_ => true))
      .value

  override def lockAccount(address: Address): Response[Boolean] =
    unlockedWallets.modify(_ - address).map(_ => Right(true))

  override def sign(message: ByteVector, address: Address, passphrase: Option[String]): Response[CryptoSignature] = {
    val p = for {
      wallet <- if (passphrase.isDefined) {
        EitherT(keyStore.unlockAccount(address, passphrase.get)).leftMap(e => invalidParams(e.toString))
      } else {
        EitherT.fromOptionF[IO, JsonRPCError, Wallet](unlockedWallets.get.map(_.get(address)),
                                                      invalidParams(s"${address} is locked"))
      }
      sig <- EitherT.right[JsonRPCError](SecP256k1.sign(getMessageToSign(message).toArray, wallet.keyPair))
    } yield sig

    p.value
  }

  override def ecRecover(message: ByteVector, signature: CryptoSignature): Response[Address] =
    IO {
      SecP256k1
        .recoverPublic(getMessageToSign(message).toArray, signature)
        .map(public => Right(Address(public.bytes.kec256)))
        .getOrElse(Left(invalidParams("unable to recover address")))
    }

  override def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): Response[ByteVector] =
    passphrase match {
      case Some(p) =>
        (for {
          wallet <- EitherT(keyStore.unlockAccount(tx.from, p)).leftMap(e => invalidRequest(e.toString))
          hash   <- EitherT(sendTransaction(tx, wallet))
        } yield hash).value

      case None =>
        unlockedWallets.get.map(_.get(tx.from)).flatMap {
          case Some(wallet) =>
            sendTransaction(tx, wallet)
          case None =>
            IO.pure(Left(invalidRequest("account is locked")))
        }
    }

  override def deleteWallet(address: Address): Response[Boolean] =
    for {
      _ <- unlockedWallets.modify(_ - address)
      r <- keyStore.deleteWallet(address)
    } yield r.leftMap(e => invalidRequest(e.toString))

  override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): Response[Boolean] =
    keyStore
      .changePassphrase(address, oldPassphrase, newPassphrase)
      .map(_.leftMap(e => invalidRequest(e.toString)))

  //////////////////////////
  //////////////////////////

  private[jbok] def getMessageToSign(message: ByteVector) = {
    val prefixed: Array[Byte] =
      0x19.toByte +: s"Ethereum Signed Message:\n${message.length}".getBytes ++: message.toArray
    ByteVector(prefixed.kec256)
  }

  private[jbok] def sendTransaction(request: TransactionRequest, wallet: Wallet): Response[ByteVector] =
    for {
      pending <- txPool.getPendingTransactions
      latestNonceOpt = Try(pending.collect {
        case ptx if ptx.stx.senderAddress(Some(0x3d.toByte)).get == wallet.address => ptx.stx.nonce
      }.max).toOption
      bn              <- history.getBestBlockNumber
      currentNonceOpt <- history.getAccount(request.from, bn).map(_.map(_.nonce.toBigInt))
      maybeNextTxNonce = latestNonceOpt.map(_ + 1).orElse(currentNonceOpt)
      tx               = request.toTransaction(maybeNextTxNonce.getOrElse(blockChainConfig.accountStartNonce))
      stx = if (bn >= blockChainConfig.eip155BlockNumber) {
        wallet.signTx(tx, Some(blockChainConfig.chainId))
      } else {
        wallet.signTx(tx, None)
      }
      _ <- txPool.addOrUpdateTransaction(stx)
    } yield Right(stx.hash)
}
