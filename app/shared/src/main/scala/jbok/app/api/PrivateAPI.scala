package jbok.app.api

import cats.effect.IO
import jbok.app.api.impl.PrivateApiImpl
import jbok.core.Configs.BlockChainConfig
import jbok.core.keystore.{KeyStore, Wallet}
import jbok.core.models.{Address, Transaction}
import jbok.core.History
import jbok.core.pool.TxPool
import jbok.crypto.signature.CryptoSignature
import jbok.network.rpc.RpcAPI
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

trait PrivateAPI extends RpcAPI {
  def importRawKey(privateKey: ByteVector, passphrase: String): Response[Address]

  def newAccount(passphrase: String): Response[Address]

  def delAccount(address: Address): Response[Boolean]

  def listAccounts: Response[List[Address]]

  def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): Response[Boolean]

  def lockAccount(address: Address): Response[Boolean]

  def sign(message: ByteVector, address: Address, passphrase: Option[String]): Response[CryptoSignature]

  def ecRecover(message: ByteVector, signature: CryptoSignature): Response[Address]

  def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): Response[ByteVector]

  def deleteWallet(address: Address): Response[Boolean]

  def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): Response[Boolean]
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
      new PrivateApiImpl(
        keyStore,
        history,
        blockChainConfig,
        txPool,
        unlockedWallets
      )
}
