package jbok.core.models

import cats.effect.IO
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

  def apply(tx: Transaction, pointSign: Byte, signatureRandom: ByteVector, signature: ByteVector, chainId: Byte): Option[SignedTransaction] = {
    val txSig = CryptoSignature(BigInt(1, signatureRandom.toArray), BigInt(1, signature.toArray), Some(pointSign))
    ???
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
    val bytesToSign: Array[Byte] =
      if (signature.v == Some(SignatureRecover.negativePointSign) || signature.v == Some(
            SignatureRecover.positivePointSign)) {
        generalTransactionBytes(tx)
      } else {
        chainSpecificTransactionBytes(tx, chainId)
      }

//    val recoveredPublicKey: Option[Array[Byte]] = SecP256k1.re.publicKey(bytesToSign, Some(chainId))

//    for {
//      key <- recoveredPublicKey
//      addrBytes = crypto.kec256(key).slice(FirstByteOfAddress, LastByteOfAddress)
//      if addrBytes.length == Address.Length
//    } yield Address(addrBytes)
    ???
  }

  private def generalTransactionBytes(tx: Transaction): Array[Byte] =
//      val receivingAddressAsArray: Array[Byte] = tx.receivingAddress.map(_.toArray).getOrElse(Array.emptyByteArray)
//      crypto.kec256(
//        rlpEncode(RLPList(
//          tx.nonce,
//          tx.gasPrice,
//          tx.gasLimit,
//          receivingAddressAsArray,
//          tx.value,
//          tx.payload)))
    ???

  private def chainSpecificTransactionBytes(tx: Transaction, chainId: Byte): Array[Byte] =
//      val receivingAddressAsArray: Array[Byte] = tx.receivingAddress.map(_.toArray).getOrElse(Array.emptyByteArray)
//      crypto.kec256(
//        rlpEncode(RLPList(
//          tx.nonce,
//          tx.gasPrice,
//          tx.gasLimit,
//          receivingAddressAsArray,
//          tx.value,
//          tx.payload,
//          chainId,
//          valueForEmptyR,
//          valueForEmptyS)))
    ???
}
