package jbok.core.models

import cats.effect.IO
import jbok.codec.codecs._
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, KeyPair, SecP256k1, SignatureRecover}
import scodec.Codec
import scodec.bits.ByteVector
import tsec.hashing.bouncy._

case class SignedTransaction(
    tx: Transaction,
    signature: CryptoSignature,
    senderAddress: Address
) {
  lazy val hash: ByteVector =
    ByteVector(Keccak256.hashPure(SignedTransaction.codec.encode(this).require.toByteArray))
}

object SignedTransaction {
  implicit val codec: Codec[SignedTransaction] = {
    Codec[Transaction] ::
      Codec[CryptoSignature] ::
      Codec[Address]
  }.as[SignedTransaction]

  def apply(
      tx: Transaction,
      pointSign: Byte,
      signatureRandom: ByteVector,
      signature: ByteVector,
      chainId: Byte
  ): Option[SignedTransaction] = {
    val txSig = CryptoSignature(BigInt(1, signatureRandom.toArray), BigInt(1, signature.toArray), Some(pointSign))
    for {
      sender <- SignedTransaction.getSender(tx, txSig, chainId)
    } yield SignedTransaction(tx, txSig, sender)
  }

  def apply(
      tx: Transaction,
      pointSign: Byte,
      signatureRandom: ByteVector,
      signature: ByteVector,
      address: Address
  ): SignedTransaction = {
    val txSig = CryptoSignature(BigInt(1, signatureRandom.toArray), BigInt(1, signature.toArray), Some(pointSign))
    SignedTransaction(tx, txSig, address)
  }

  def sign(tx: Transaction, keyPair: KeyPair, chainId: Option[Byte]): SignedTransaction = {
    val bytes = bytesToSign(tx, chainId)
    val sig = SecP256k1.sign[IO](bytes, keyPair).unsafeRunSync()
    val pub = keyPair.public.uncompressed
    val address = Address(pub.kec256)
    SignedTransaction(tx, sig, address)
  }

  private def bytesToSign(tx: Transaction, chainId: Option[Byte]): ByteVector =
    Transaction.codec.encode(tx).require.bytes.kec256

  private def getSender(tx: Transaction, signature: CryptoSignature, chainId: Byte): Option[Address] = {
    val bytesToSign =
      if (signature.v == Some(SignatureRecover.negativePointSign) || signature.v == Some(
            SignatureRecover.positivePointSign)) {
        generalTransactionBytes(tx)
      } else {
        chainSpecificTransactionBytes(tx, chainId)
      }

    for {
      recoveredPublicKey <- SecP256k1.recoverPublic(signature, bytesToSign, Some(chainId))
    } yield Address(recoveredPublicKey.uncompressed.kec256)
  }

  private def generalTransactionBytes(tx: Transaction): ByteVector = {
    val codec = codecBigInt :: codecBigInt :: codecBigInt :: codecBytes :: codecBigInt :: codecBytes
    import shapeless._
    val hlist = tx.nonce :: tx.gasPrice :: tx.gasLimit :: tx.receivingAddress
      .map(_.bytes)
      .getOrElse(ByteVector.empty) :: tx.value :: tx.payload :: HNil
    codec.encode(hlist).require.bytes
  }

  private def chainSpecificTransactionBytes(tx: Transaction, chainId: Byte): ByteVector = {

    val codec = codecBigInt :: codecBigInt :: codecBigInt :: codecBytes :: codecBigInt :: codecBytes :: codecByte :: codecBigInt :: codecBigInt
    import shapeless._
    val hlist = tx.nonce :: tx.gasPrice :: tx.gasLimit :: tx.receivingAddress
      .map(_.bytes)
      .getOrElse(ByteVector.empty) :: tx.value :: tx.payload :: chainId :: BigInt(0) :: BigInt(0) :: HNil
    codec.encode(hlist).require.bytes
  }
}
