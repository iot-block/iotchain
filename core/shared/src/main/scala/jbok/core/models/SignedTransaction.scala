package jbok.core.models

import io.circe.{Decoder, Encoder}
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.codec.json.implicits._
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, ECDSA, KeyPair, Signature}
import scodec.bits.ByteVector
import shapeless._

case class SignedTransaction(
    nonce: BigInt,
    gasPrice: BigInt,
    gasLimit: BigInt,
    receivingAddress: Address,
    value: BigInt,
    payload: ByteVector,
    v: BigInt,
    r: BigInt,
    s: BigInt
) {
  lazy val hash: ByteVector =
    RlpCodec.encode(this).require.bytes.kec256

  def senderAddress(chainId: Option[Byte]): Option[Address] =
    SignedTransaction.getSender(this, chainId)

  def isContractInit: Boolean = receivingAddress == Address.empty
}

object SignedTransaction {
  implicit val txJsonEncoder: Encoder[SignedTransaction] = deriveEncoder[SignedTransaction]

  implicit val txJsonDecoder: Decoder[SignedTransaction] = deriveDecoder[SignedTransaction]

  def apply(
      tx: Transaction,
      v: BigInt,
      r: BigInt,
      s: BigInt
  ): SignedTransaction = SignedTransaction(
    tx.nonce,
    tx.gasPrice,
    tx.gasLimit,
    tx.receivingAddress.getOrElse(Address.empty),
    tx.value,
    tx.payload,
    v,
    r,
    s
  )

  def apply(
      tx: Transaction,
      v: Byte,
      r: ByteVector,
      s: ByteVector
  ): SignedTransaction = apply(tx, BigInt(1, Array(v)), BigInt(1, r.toArray), BigInt(1, s.toArray))

  def sign(tx: Transaction, keyPair: KeyPair, chainId: Option[Byte] = None): SignedTransaction = {
    val stx = new SignedTransaction(
      tx.nonce,
      tx.gasPrice,
      tx.gasLimit,
      tx.receivingAddress.getOrElse(Address(ByteVector.empty)),
      tx.value,
      tx.payload,
      BigInt(0),
      BigInt(0),
      BigInt(0)
    )
    val bytes = bytesToSign(stx, chainId)
    val sig   = Signature[ECDSA].sign(bytes.toArray, keyPair, chainId).unsafeRunSync()
    stx.copy(v = BigInt(1, Array(sig.v)), r = sig.r, s = sig.s)
  }

  private def bytesToSign(stx: SignedTransaction, chainId: Option[Byte]): ByteVector =
    chainId match {
      case Some(id) => chainSpecificTransactionBytes(stx, id)
      case None     => generalTransactionBytes(stx)
    }

  private def recoverPublicKey(stx: SignedTransaction, chainId: Option[Byte]): Option[KeyPair.Public] = {
    val bytesToSign = SignedTransaction.bytesToSign(stx, chainId)
    val txSig       = CryptoSignature(stx.r, stx.s, stx.v.toByte)

    Signature[ECDSA].recoverPublic(bytesToSign.toArray, txSig, chainId)
  }

  private[jbok] def getSender(stx: SignedTransaction, chainId: Option[Byte] = None): Option[Address] =
    SignedTransaction.recoverPublicKey(stx, chainId).map(pk => Address(pk.bytes.kec256))

  private def generalTransactionBytes(stx: SignedTransaction): ByteVector = {
    val hlist = stx.nonce :: stx.gasPrice :: stx.gasLimit :: stx.receivingAddress :: stx.value :: stx.payload :: HNil
    RlpCodec.encode(hlist).require.bytes.kec256
  }

  private def chainSpecificTransactionBytes(stx: SignedTransaction, chainId: Byte): ByteVector = {
    val hlist = stx.nonce :: stx.gasPrice :: stx.gasLimit :: stx.receivingAddress :: stx.value :: stx.payload :: chainId :: BigInt(
      0) :: BigInt(0) :: HNil
    RlpCodec.encode(hlist).require.bytes.kec256
  }
}
