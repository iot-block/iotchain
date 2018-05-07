package jbok.testkit

import jbok.crypto.hashing.{HashType, MultiHash}

object Cast {
  val names = Vector(
    "god",
    "alice",
    "bob",
    "carol",
    "david",
    "ed"
  )

  val hash2name: Map[MultiHash, String] = names.map(x => MultiHash.hash(x, HashType.sha256) -> x).toMap

  val name2hash: Map[String, MultiHash] = {
    val reversed = hash2name.toList.map(_.swap).toMap
    require(reversed.size == hash2name.size)
    reversed
  }
}
