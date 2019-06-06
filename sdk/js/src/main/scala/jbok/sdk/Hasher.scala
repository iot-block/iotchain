package jbok.sdk

import jbok.crypto.hash.{CryptoHasher, Keccak256}
import scodec.bits.ByteVector

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.typedarray._

@JSExportTopLevel("Hasher")
@JSExportAll
object Hasher {
  def kec256(bytes: Int8Array): String =
    CryptoHasher[Keccak256].hash(ByteVector(bytes.toArray)).toHex
}
