package jbok.persistent
import java.nio.charset.StandardCharsets

import scodec.bits.ByteVector

final case class ColumnFamily(name: String) {
  val bytes: Array[Byte] = name.getBytes(StandardCharsets.UTF_8)

  val bv: ByteVector = ByteVector(bytes)
}

object ColumnFamily {
  val default: ColumnFamily = ColumnFamily("default")
}
