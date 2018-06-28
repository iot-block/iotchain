package jbok.core.messages

import jbok.p2p.PeerId
import scodec.bits.ByteVector

trait Message {
  def asBytes: ByteVector = ???
}

case class MessageFromPeer(message: Message, peerId: PeerId)
case class MessageToPeer(message: Message, peerId: PeerId)
