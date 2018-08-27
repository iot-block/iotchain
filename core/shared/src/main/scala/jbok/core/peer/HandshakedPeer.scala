package jbok.core.peer

import java.net.InetSocketAddress

case class HandshakedPeer(
    peerId: PeerId,
    remote: InetSocketAddress,
    peerInfo: PeerInfo
)

object HandshakedPeer {
  def apply(remote: InetSocketAddress, peerInfo: PeerInfo): HandshakedPeer =
    HandshakedPeer(PeerId(remote.toString), remote, peerInfo)
}
