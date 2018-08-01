package jbok.core.models

import cats.effect.IO
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, KeyPair, SecP256k1, SignatureRecover}
import scodec.bits.ByteVector
import shapeless._

case class SignedTransaction(
    tx: Transaction,
    signature: CryptoSignature,
    senderAddress: Address
) {
  lazy val hash: ByteVector =
    RlpCodec.encode(this).require.bytes.kec256
}

object SignedTransaction {
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
    RlpCodec.encode(tx).require.bytes.kec256

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
    val hlist = tx.nonce :: tx.gasPrice :: tx.gasLimit :: tx.receivingAddress
      .map(_.bytes)
      .getOrElse(ByteVector.empty) :: tx.value :: tx.payload :: HNil
    RlpCodec.encode(hlist).require.bytes
  }

  private def chainSpecificTransactionBytes(tx: Transaction, chainId: Byte): ByteVector = {
    val hlist = tx.nonce :: tx.gasPrice :: tx.gasLimit :: tx.receivingAddress
      .map(_.bytes)
      .getOrElse(ByteVector.empty) :: tx.value :: tx.payload :: chainId :: BigInt(0) :: BigInt(0) :: HNil
    RlpCodec.encode(hlist).require.bytes
  }
}
