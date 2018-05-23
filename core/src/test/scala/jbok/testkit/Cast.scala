package jbok.testkit

import jbok.crypto._
import jbok.crypto.hashing.{Hash, Hashing}

object Cast {
  val names = Vector(
    "god",
    "alice",
    "bob",
    "carol",
    "david",
    "ed"
  )

  val hash2name: Map[Hash, String] = names.map(x => Hashing.hash(x.utf8bytes)(Hashing.sha256) -> x).toMap

  val name2hash: Map[String, Hash] = {
    val reversed = hash2name.toList.map(_.swap).toMap
    require(reversed.size == hash2name.size)
    reversed
  }
}
