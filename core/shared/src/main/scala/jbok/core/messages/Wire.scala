package jbok.core.messages

import jbok.codec.codecs._
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.uint8
import scodec.codecs.uint16

case class Hello(
    p2pVersion: Int,
    clientId: String,
    listenPort: Int,
    nodeId: ByteVector
) extends Message

object Hello {
  implicit val codec: Codec[Hello] = (uint8 :: codecString :: uint16 :: codecBytes).as[Hello]
}

case class Status(version: Int, networkId: Int, bestHash: ByteVector, genesisHash: ByteVector) extends Message

object Status {
  implicit val codec = (uint8 :: uint8 :: codecBytes :: codecBytes).as[Status]
}
