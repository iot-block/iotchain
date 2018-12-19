package jbok.app.api

import io.circe.generic.JsonCodec
import jbok.codec.json.implicits._
import jbok.core.models.{Address, Transaction}
import jbok.core.peer.PeerNode
import jbok.crypto.signature._
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("TransactionRequest")
@JsonCodec
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

trait PersonalAPI[F[_]] {
  def importRawKey(privateKey: ByteVector, passphrase: String): F[Address]

  def newAccount(passphrase: String): F[Address]

  def delAccount(address: Address): F[Boolean]

  def listAccounts: F[List[Address]]

  def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): F[Boolean]

  def lockAccount(address: Address): F[Boolean]

  def sign(message: ByteVector, address: Address, passphrase: Option[String]): F[CryptoSignature]

  def ecRecover(message: ByteVector, signature: CryptoSignature): F[Address]

  def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): F[ByteVector]

  def deleteWallet(address: Address): F[Boolean]

  def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Boolean]
}
