package jbok.core.messages

import scodec.bits._

case class Status(version: Int, networkId: Int, bestHash: ByteVector, genesisHash: ByteVector)

