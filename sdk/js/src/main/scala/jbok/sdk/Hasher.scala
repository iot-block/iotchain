package jbok.sdk

import jbok.crypto.hash.{CryptoHasher, Keccak256}
import scodec.bits.ByteVector

import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.typedarray._

@JSExportTopLevel("Hasher")
@JSExportAll
object Hasher {
  implicit private def byteVectorToTypedArray(bytes: ByteVector): Int8Array =
    new Int8Array(bytes.toArray.toJSArray)

  private def Int8Array8Bytes(bytes: Int8Array): ByteVector =
    ByteVector(int8Array2ByteArray(bytes))

  def kec256(bytes: Int8Array): String =
    CryptoHasher[Keccak256].hash(Int8Array8Bytes(bytes)).toHex
}
