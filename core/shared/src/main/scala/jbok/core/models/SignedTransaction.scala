package jbok.core.models

import cats.effect.Sync
import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._
import jbok.codec.json.implicits._
import jbok.codec.rlp.RlpEncoded
import jbok.codec.rlp.implicits._
import jbok.common.math.N
import jbok.common.math.implicits._
import jbok.core.validators.TxInvalid.TxSignatureInvalid
import jbok.crypto._
import jbok.crypto.signature._
import scodec.bits.ByteVector

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("SignedTransaction")
@JSExportAll
//@ConfiguredJsonCodec
final case class SignedTransaction(
    nonce: N,
    gasPrice: N,
    gasLimit: N,
    receivingAddress: Address,
    value: N,
    payload: ByteVector,
    v: N,
    r: N,
    s: N
) {
  lazy val bytes: RlpEncoded = this.encoded

  lazy val hash: ByteVector = bytes.bytes.kec256

  lazy val chainIdOpt: Option[ChainId] = ECDSACommon.getChainId(v.toBigInt).map(bi => ChainId(bi))

  lazy val senderAddress: Option[Address] = chainIdOpt.flatMap(chainId => SignedTransaction.getSender(this, chainId))

  def getSenderOrThrow[F[_]: Sync]: F[Address] =
    Sync[F].fromOption(senderAddress, TxSignatureInvalid)

  def isContractInit: Boolean =
    receivingAddress == Address.empty
}


object SignedTransaction {
  implicit val txEncoder:Encoder[SignedTransaction] = Encoder.forProduct10(
    "nonce",
    "gasPrice",
    "gasLimit",
    "receivingAddress",
    "value",
    "payload",
    "v",
    "r",
    "s",
    "hash")((tx:SignedTransaction) => (tx.nonce,tx.gasPrice,tx.gasLimit,tx.receivingAddress,tx.value,tx.payload,tx.v,tx.r,tx.s,tx.hash))
  implicit val txDecoder:Decoder[SignedTransaction] = Decoder.forProduct9(
    "nonce",
    "gasPrice",
    "gasLimit",
    "receivingAddress",
    "value",
    "payload",
    "v",
    "r",
    "s"
  )((
    nonce: N,
    gasPrice: N,
    gasLimit: N,
    receivingAddress: Address,
    value: N,
    payload: ByteVector,
    v: N,
    r: N,
    s: N
  )=>SignedTransaction(nonce,gasPrice,gasLimit,receivingAddress,value,payload,v,r,s))

  def apply(
      tx: Transaction,
      v: N,
      r: N,
      s: N
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
  ): SignedTransaction = apply(tx, N(Array(v)), N(r.toArray), N(s.toArray))

  def sign[F[_]: Sync](tx: Transaction, keyPair: KeyPair, chainId: ChainId): F[SignedTransaction] = {
    val stx = new SignedTransaction(
      tx.nonce,
      tx.gasPrice,
      tx.gasLimit,
      tx.receivingAddress.getOrElse(Address(ByteVector.empty)),
      tx.value,
      tx.payload,
      N(0),
      N(0),
      N(0)
    )
    val bytes = bytesToSign(stx, chainId)
    Signature[ECDSA].sign[F](bytes.toArray, keyPair, chainId.value.toBigInt).map(sig => stx.copy(v = sig.v, r = sig.r, s = sig.s))
  }

  private def bytesToSign(stx: SignedTransaction, chainId: ChainId): ByteVector =
    (stx.nonce, stx.gasPrice, stx.gasLimit, stx.receivingAddress, stx.value, stx.payload, chainId.value, N(0), N(0)).encoded.bytes.kec256

  private def recoverPublicKey(stx: SignedTransaction, chainId: ChainId): Option[KeyPair.Public] = {
    val bytesToSign = SignedTransaction.bytesToSign(stx, chainId)
    val txSig       = CryptoSignature(stx.r.toBigInt, stx.s.toBigInt, stx.v.toBigInt)

    Signature[ECDSA].recoverPublic(bytesToSign.toArray, txSig, chainId.value.toBigInt)
  }

  private def getSender(stx: SignedTransaction, chainId: ChainId): Option[Address] =
    recoverPublicKey(stx, chainId).map(pk => Address(pk.bytes.kec256))

}
