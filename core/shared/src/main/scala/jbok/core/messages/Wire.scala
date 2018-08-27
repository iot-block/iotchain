package jbok.core.messages

import scodec.bits.ByteVector

case class Hello(
    p2pVersion: Int,
    clientId: String,
    listenPort: Int,
    nodeId: ByteVector
) extends Message

case class Status(version: Int, networkId: Int, bestHash: ByteVector, genesisHash: ByteVector) extends Message
