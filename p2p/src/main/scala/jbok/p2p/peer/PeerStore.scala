package jbok.p2p.peer

trait PeerStore[F[_]] {
  def get(id: PeerId): F[Option[PeerInfo]]

  def put(peer: PeerInfo): F[Unit]

  def del(id: PeerId): F[Unit]

  def has(id: PeerId): F[Boolean]

  def ids: F[Seq[PeerId]]

  def clear(): F[Unit]

  def banPeer(id: PeerId): F[Unit]

  def isBanned(id: PeerId): F[Boolean]
}
