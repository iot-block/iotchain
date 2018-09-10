package jbok.app.api

import cats.effect.IO
import jbok.core.Configs.BlockChainConfig
import jbok.core.History
import jbok.core.keystore.{KeyStore, Wallet}
import jbok.core.models.{Address, Transaction}
import jbok.core.pool.TxPool
import jbok.crypto._
import jbok.crypto.signature.CryptoSignature
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.network.json.JsonRPCResponse
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.util.Try

case class TransactionRequest(
    from: Address,
    to: Option[Address] = None,
    value: Option[BigInt] = None,
    gasLimit: Option[BigInt] = None,
    gasPrice: Option[BigInt] = None,
    nonce: Option[BigInt] = None,
    data: Option[ByteVector] = None
) {

  private val defaultGasPrice: BigInt = 2 * BigInt(10).pow(10)
  private val defaultGasLimit: BigInt = 90000

  def toTransaction(defaultNonce: BigInt): Transaction =
    Transaction(
      nonce = nonce.getOrElse(defaultNonce),
      gasPrice = gasPrice.getOrElse(defaultGasPrice),
      gasLimit = gasLimit.getOrElse(defaultGasLimit),
      receivingAddress = to,
      value = value.getOrElse(BigInt(0)),
      payload = data.getOrElse(ByteVector.empty)
    )
}

trait PrivateAPI {
  def importRawKey(privateKey: ByteVector, passphrase: String): IO[Address]

  def newAccount(passphrase: String): IO[Address]

  def delAccount(address: Address): IO[Boolean]

  def listAccounts: IO[List[Address]]

  def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): IO[Boolean]

  def lockAccount(address: Address): IO[Boolean]

  def sign(message: ByteVector, address: Address, passphrase: Option[String]): IO[CryptoSignature]

  def ecRecover(message: ByteVector, signature: CryptoSignature): IO[Address]

  def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): IO[ByteVector]

  def deleteWallet(address: Address): IO[Boolean]

  def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): IO[Boolean]
}

object PrivateAPI {
  def apply(
      keyStore: KeyStore[IO],
      history: History[IO],
      blockChainConfig: BlockChainConfig,
      txPool: TxPool[IO],
  ): IO[PrivateAPI] =
    for {
      unlockedWallets <- fs2.async.refOf[IO, Map[Address, Wallet]](Map.empty)
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
            _      <- unlockedWallets.modify(_ + (address -> wallet))
          } yield true

        override def lockAccount(address: Address): IO[Boolean] =
          unlockedWallets.modify(_ - address).map(_ => true)

        override def sign(message: ByteVector, address: Address, passphrase: Option[String]): IO[CryptoSignature] =
          for {
            wallet <- if (passphrase.isDefined) {
              keyStore.unlockAccount(address, passphrase.get)
            } else {
              unlockedWallets.get.map(_(address))
            }
            sig <- SecP256k1.sign(getMessageToSign(message).toArray, wallet.keyPair)
          } yield sig

        override def ecRecover(message: ByteVector, signature: CryptoSignature): IO[Address] =
          IO {
            SecP256k1
              .recoverPublic(getMessageToSign(message).toArray, signature)
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
                  IO.raiseError(JsonRPCResponse.invalidRequest("account is locked"))
              }
          }

        override def deleteWallet(address: Address): IO[Boolean] =
          for {
            _ <- unlockedWallets.modify(_ - address)
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
          } yield stx.hash
      }
}
