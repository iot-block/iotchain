package jbok.sdk

import jbok.codec.rlp.RlpEncoded
import jbok.codec.rlp.implicits._
import jbok.core.models.{BlockHeader, SignedTransaction}
import scodec.bits.BitVector

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.typedarray.Int8Array

@JSExportTopLevel("BinaryCodec")
@JSExportAll
object BinaryCodec {
  def encodeBlockHeader(header: BlockHeader): Int8Array =
    new Int8Array(header.encoded.byteArray.toJSArray)

  def decodeBlockHeader(bytes: Int8Array): js.UndefOr[BlockHeader] =
    RlpEncoded.coerce(BitVector(bytes.toArray)).decoded[BlockHeader].toOption.orUndefined

  def encodeTx(tx: SignedTransaction): Int8Array =
    new Int8Array(tx.encoded.byteArray.toJSArray)

  def decodeTx(bytes: Int8Array): js.UndefOr[SignedTransaction] =
    RlpEncoded.coerce(BitVector(bytes.toArray)).decoded[SignedTransaction].toOption.orUndefined
}
