package jbok.core.api.impl

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import fs2.async.Ref
import jbok.core.api.{PrivateAPI, TransactionRequest}
import jbok.core.configs.BlockChainConfig
import jbok.core.keystore.{KeyStore, Wallet}
import jbok.core.models.Address
import jbok.core.{BlockChain, TxPool}
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, SecP256k1}
import jbok.network.json.JsonRPCError
import jbok.network.json.JsonRPCResponse._
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.util.Try

class PrivateApiImpl[F[_]](
    keyStore: KeyStore[F],
    blockchain: BlockChain[F],
    blockChainConfig: BlockChainConfig,
    txPool: TxPool[F],
    unlockedWallets: Ref[F, Map[Address, Wallet]]
)(implicit F: Async[F])
    extends PrivateAPI[F] {
  override def importRawKey(privateKey: ByteVector, passphrase: String): R[Address] =
    keyStore.importPrivateKey(privateKey, passphrase).map(_.leftMap(e => invalidRequest(e.toString)))

  override def newAccount(passphrase: String): R[Address] =
    keyStore.newAccount(passphrase).map(_.leftMap(e => invalidRequest(e.toString)))

  override def delAccount(address: Address): R[Boolean] =
    keyStore.deleteWallet(address).map(_.leftMap(e => invalidRequest(e.toString)))

  override def listAccounts: R[List[Address]] =
    keyStore.listAccounts.map(_.leftMap(e => invalidRequest(e.toString)))

  override def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): R[Boolean] =
    EitherT(keyStore.unlockAccount(address, passphrase))
      .leftMap(e => invalidRequest(e.toString))
      .semiflatMap(wallet => unlockedWallets.modify(_ + (address -> wallet)).map(_ => true))
      .value

  override def lockAccount(address: Address): R[Boolean] =
    unlockedWallets.modify(_ - address).map(_ => Right(true))

  override def sign(message: ByteVector, address: Address, passphrase: Option[String]): R[CryptoSignature] = {
    val p = for {
      wallet <- if (passphrase.isDefined) {
        EitherT(keyStore.unlockAccount(address, passphrase.get)).leftMap(e => invalidParams(e.toString))
      } else {
        EitherT.fromOptionF[F, JsonRPCError, Wallet](unlockedWallets.get.map(_.get(address)), invalidParams(""))
      }
      sig <- EitherT.right[JsonRPCError](SecP256k1.sign[F](getMessageToSign(message), wallet.keyPair))
    } yield sig

    p.value
  }

  override def ecRecover(message: ByteVector, signature: CryptoSignature): R[Address] =
    F.delay {
      SecP256k1
        .recoverPublic(signature, getMessageToSign(message))
        .map(public => Right(Address(public.uncompressed.kec256)))
        .getOrElse(Left(invalidParams("unable to recover address")))
    }

  override def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): R[ByteVector] = passphrase match {
    case Some(p) =>
      EitherT(keyStore.unlockAccount(tx.from, p))
        .leftMap { e =>
          invalidRequest(e.toString)
        }
        .semiflatMap(wallet => sendTransaction(tx, wallet))
        .value

    case None =>
      unlockedWallets.get.map(_.get(tx.from)).flatMap {
        case Some(wallet) =>
          sendTransaction(tx, wallet).map(hash => Right(hash))
        case None =>
          F.pure(Left(invalidRequest("account is locked")))
      }
  }

  override def deleteWallet(address: Address): R[Boolean] =
    for {
      _ <- unlockedWallets.modify(_ - address)
      r <- keyStore.deleteWallet(address)
    } yield r.leftMap(e => invalidRequest(e.toString))

  override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): R[Boolean] =
    keyStore
      .changePassphrase(address, oldPassphrase, newPassphrase)
      .map(_.leftMap(e => invalidRequest(e.toString)))

  //////////////////////////

  private[jbok] def getMessageToSign(message: ByteVector) = {
    val prefixed: Array[Byte] =
      0x19.toByte +: s"Ethereum Signed Message:\n${message.length}".getBytes ++: message.toArray
    ByteVector(prefixed.kec256)
  }

  private[jbok] def sendTransaction(request: TransactionRequest, wallet: Wallet): F[ByteVector] =
    for {
      pending <- txPool.getPendingTransactions
      latestNonceOpt = Try(
        pending.collect { case ptx if ptx.stx.senderAddress == wallet.address => ptx.stx.tx.nonce }.max).toOption
      bn <- blockchain.getBestBlockNumber
      currentNonceOpt <- blockchain.getAccount(request.from, bn).map(_.map(_.nonce.toBigInt))
      maybeNextTxNonce = latestNonceOpt.map(_ + 1).orElse(currentNonceOpt)
      tx = request.toTransaction(maybeNextTxNonce.getOrElse(blockChainConfig.accountStartNonce))
      stx = if (bn >= blockChainConfig.eip155BlockNumber) {
        wallet.signTx(tx, Some(blockChainConfig.chainId))
      } else {
        wallet.signTx(tx, None)
      }
      _ <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash
}
