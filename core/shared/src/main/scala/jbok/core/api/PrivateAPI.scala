package jbok.core.api

import cats.effect.Concurrent
import cats.implicits._
import jbok.core.{BlockChain, TxPool}
import jbok.core.api.impl.PrivateApiImpl
import jbok.core.configs.BlockChainConfig
import jbok.core.keystore.{KeyStore, Wallet}
import jbok.core.models.{Address, Transaction}
import jbok.crypto.signature.CryptoSignature
import jbok.network.JsonRpcAPI
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration

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

trait PrivateAPI[F[_]] extends JsonRpcAPI[F] {
  def importRawKey(privateKey: ByteVector, passphrase: String): R[Address]

  def newAccount(passphrase: String): R[Address]

  def delAccount(address: Address): R[Boolean]

  def listAccounts: R[List[Address]]

  def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): R[Boolean]

  def lockAccount(address: Address): R[Boolean]

  def sign(message: ByteVector, address: Address, passphrase: Option[String]): R[CryptoSignature]

  def ecRecover(message: ByteVector, signature: CryptoSignature): R[Address]

  def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): R[ByteVector]

  def deleteWallet(address: Address): R[Boolean]

  def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): R[Boolean]
}

object PrivateAPI {
  def apply[F[_]: Concurrent](
      keyStore: KeyStore[F],
      blockchain: BlockChain[F],
      blockChainConfig: BlockChainConfig,
      txPool: TxPool[F],
  ): F[PrivateAPI[F]] =
    for {
      unlockedWallets <- fs2.async.refOf[F, Map[Address, Wallet]](Map.empty)
    } yield
      new PrivateApiImpl[F](
        keyStore,
        blockchain,
        blockChainConfig,
        txPool,
        unlockedWallets
      )
}
