package jbok.sdk

import jbok.codec.rlp.implicits._
import jbok.core.models.{BlockHeader, SignedTransaction}
import scodec.bits.ByteVector

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.typedarray.{Int8Array, int8Array2ByteArray}

@JSExportTopLevel("BinaryCodec")
@JSExportAll
object BinaryCodec {
  implicit private def byteVectorToTypedArray(bytes: ByteVector): Int8Array =
    new Int8Array(bytes.toArray.toJSArray)

  private def Int8Array8Bytes(bytes: Int8Array): ByteVector =
    ByteVector(int8Array2ByteArray(bytes))

  def encodeBlockHeader(header: BlockHeader): Int8Array =
    header.asValidBytes

  def decodeBlockHeader(bytes: Int8Array): js.UndefOr[BlockHeader] =
    Int8Array8Bytes(bytes).asOpt[BlockHeader].orUndefined

  def encodeTx(tx: SignedTransaction): Int8Array =
    tx.asValidBytes

  def decodeTx(bytes: Int8Array): js.UndefOr[SignedTransaction] =
    Int8Array8Bytes(bytes).asOpt[SignedTransaction].orUndefined
}
