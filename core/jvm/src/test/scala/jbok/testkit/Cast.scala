package jbok.testkit

import jbok.crypto._
import scodec.bits.ByteVector

object Cast {
  val names = Vector(
    "god",
    "alice",
    "bob",
    "carol",
    "david",
    "ed"
  )

  val hash2name: Map[ByteVector, String] = names.map(x => x.utf8bytes.kec256 -> x).toMap

  val name2hash: Map[String, ByteVector] = {
    val reversed = hash2name.toList.map(_.swap).toMap
    require(reversed.size == hash2name.size)
    reversed
  }
}
