package jbok.p2p.peer

trait PeerManager[F[_]] {
  def knownPeers: F[List[PeerInfo]]

  def connectedPeers: F[List[PeerInfo]]

  def discover(): F[Unit]

  def connect(): F[Unit]
}
