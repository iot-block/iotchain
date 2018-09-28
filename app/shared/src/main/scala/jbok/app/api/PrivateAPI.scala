package jbok.app.api

import cats.effect.IO
import jbok.core.models.{Address, Transaction}
import jbok.crypto.signature._
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

