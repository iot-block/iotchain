package jbok.core.models

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.crypto._
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.crypto.signature.{KeyPair, CryptoSignature}
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

  def isContractInit: Boolean = receivingAddress.equals(Address.empty)
}

object SignedTransaction {
  def apply(
      tx: Transaction,
      pointSign: Byte,
      signatureRandom: ByteVector,
      signature: ByteVector,
      chainId: Byte
  ): SignedTransaction =
    new SignedTransaction(
      tx.nonce,
      tx.gasPrice,
      tx.gasLimit,
      tx.receivingAddress.getOrElse(Address.empty),
      tx.value,
      tx.payload,
      BigInt(1, Array(pointSign)),
      BigInt(1, signatureRandom.toArray),
      BigInt(1, signature.toArray)
    )

  def apply(
      tx: Transaction,
      pointSign: Byte,
      signatureRandom: ByteVector,
      signature: ByteVector,
      address: Address
  ): SignedTransaction = {
    val txSig = CryptoSignature(BigInt(1, signatureRandom.toArray), BigInt(1, signature.toArray), pointSign)
    new SignedTransaction(
      tx.nonce,
      tx.gasPrice,
      tx.gasLimit,
      tx.receivingAddress.getOrElse(Address(ByteVector.empty)),
      tx.value,
      tx.payload,
      BigInt(1, Array(pointSign)),
      BigInt(1, signatureRandom.toArray),
      BigInt(1, signature.toArray)
    )
  }

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
    val sig   = SecP256k1.sign(bytes.toArray, keyPair, chainId).unsafeRunSync()
    stx.copy(v = BigInt(1, Array(sig.v)), r = sig.r, s = sig.s)
  }

  def verify(stx: SignedTransaction, chainId: Option[Byte], sender: Address): Boolean = {
    val bytesToSign = SignedTransaction.bytesToSign(stx, chainId)
    SignedTransaction.getSender(stx, chainId).contains(sender)
  }

  private def bytesToSign(stx: SignedTransaction, chainId: Option[Byte]): ByteVector =
    chainId match {
      case Some(id) => chainSpecificTransactionBytes(stx, id)
      case None     => generalTransactionBytes(stx)
    }

  private def recoverPublicKey(stx: SignedTransaction, chainId: Option[Byte]): Option[KeyPair.Public] = {
    val bytesToSign = SignedTransaction.bytesToSign(stx, chainId)
    val txSig       = CryptoSignature(stx.r, stx.s, stx.v.toByte)

    SecP256k1.recoverPublic(bytesToSign.toArray, txSig, chainId)
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
