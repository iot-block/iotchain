package jbok.core.models

import cats.effect.IO
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, KeyPair, SecP256k1}
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
    val sig = SecP256k1.sign[IO](bytes, keyPair).unsafeRunSync()
    val pub = keyPair.public.uncompressed
    val address = Address(pub.kec256)
    SignedTransaction(tx, sig, address)
  }

  private def bytesToSign(tx: Transaction, chainId: Option[Byte]): ByteVector =
    Transaction.codec.encode(tx).require.bytes.kec256
}
