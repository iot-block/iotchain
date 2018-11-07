package jbok.core.store

import scodec.bits.ByteVector

object namespaces {
  val empty = ByteVector.empty

  // chain data
  val BlockHeader: ByteVector = ByteVector("h".getBytes)
  val BlockBody: ByteVector   = ByteVector("b".getBytes)
  val Receipts: ByteVector    = ByteVector("r".getBytes)

  // account, storage and code
  val Node: ByteVector = ByteVector("n".getBytes)
  val Code: ByteVector = ByteVector("c".getBytes)

  // state
  val TotalDifficulty: ByteVector   = ByteVector("t".getBytes)
  val AppStateNamespace: ByteVector = ByteVector("s".getBytes)

  // discovered peers
  val Peer: ByteVector = ByteVector("k".getBytes)

  // mapping
  val NumberHash: ByteVector = ByteVector("i".getBytes)
  val TxLocation: ByteVector = ByteVector("l".getBytes)
}
