package jbok.core.store

import scodec.bits._

object Namespaces {
  val Receipts: ByteVector = hex"r"
  val BlockHeader: ByteVector = hex"h"
  val BlockBody: ByteVector = hex"b"
  val NodeNamespace: ByteVector = hex"n"
  val CodeNamespace: ByteVector = hex"c"
  val TotalDifficultyNamespace: ByteVector = hex"t"
  val AppStateNamespace: ByteVector = hex"s"
  val KnownNodesNamespace: ByteVector = hex"k"
  val Heights: ByteVector = hex"i"
  val FastSyncStateNamespace: ByteVector = hex"h"
  val TransactionLocation: ByteVector = hex"l"
}
