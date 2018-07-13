package jbok.core.models

import cats.effect.IO
import jbok.crypto.signature.{CryptoSignature, Ed25519, KeyPair}
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

  def sign(tx: Transaction, keyPair: KeyPair, chainId: Option[Byte]): SignedTransaction = {
    val bytes = bytesToSign(tx, chainId)
    val sig = Ed25519.sign[IO](bytes, keyPair.secret).unsafeRunSync()
    val pub = keyPair.public.bytes
    val address = Address(Keccak256.hashPure(pub))
    SignedTransaction(tx, sig, address)
  }

  private def bytesToSign(tx: Transaction, chainId: Option[Byte]): Array[Byte] =
    Transaction.codec.encode(tx).require.bytes.toArray
}
