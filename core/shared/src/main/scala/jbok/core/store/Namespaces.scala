package jbok.core.store

import scodec.bits._
import jbok.codec._

object Namespaces {
  val Receipts: ByteVector = "r".utf8Bytes
  val BlockHeader: ByteVector = "h".utf8Bytes
  val BlockBody: ByteVector = "b".utf8Bytes
  val NodeNamespace: ByteVector = "n".utf8Bytes
  val CodeNamespace: ByteVector = "c".utf8Bytes
  val TotalDifficulty: ByteVector = "t".utf8Bytes
  val AppStateNamespace: ByteVector = "s".utf8Bytes
  val KnownNodesNamespace: ByteVector = "k".utf8Bytes
  val Heights: ByteVector = "i".utf8Bytes
  val FastSync: ByteVector = "h".utf8Bytes
  val TransactionLocation: ByteVector = "l".utf8Bytes
}
