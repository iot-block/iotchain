package jbok.core.peer

import java.net.InetSocketAddress

case class HandshakedPeer(
    peerId: PeerId,
    remote: InetSocketAddress,
    peerInfo: PeerInfo,
    incoming: Boolean
)

object HandshakedPeer {
  def apply(remote: InetSocketAddress, peerInfo: PeerInfo, incoming: Boolean): HandshakedPeer =
    HandshakedPeer(PeerId(remote.toString), remote, peerInfo, incoming)
}
