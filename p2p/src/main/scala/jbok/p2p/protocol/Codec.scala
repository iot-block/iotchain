package jbok.p2p.protocol

import akka.stream.scaladsl.BidiFlow
import akka.util.ByteString

object Codec {
  def apply[M](fromBytes: ByteString => M, toBytes: M => ByteString): BidiFlow[ByteString, M, M, ByteString, Any] =
    BidiFlow.fromFunctions(fromBytes, toBytes)

  val dummy = Codec.apply[ByteString](identity, identity)
}
