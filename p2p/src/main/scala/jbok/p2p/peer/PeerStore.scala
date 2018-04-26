package jbok.p2p.peer

import cats.Id

import scala.collection.mutable

trait PeerStore[F[_]] {
  def get(id: PeerId): F[Option[PeerInfo]]

  def put(peer: PeerInfo): F[Unit]

  def del(id: PeerId): F[Unit]

  def has(id: PeerId): F[Boolean]

  def ids: F[List[PeerId]]

  def clear(): F[Unit]

  def banPeer(peer: PeerInfo): F[Unit]

  def isBanned(id: PeerId): F[Boolean]
}

class InMemoryPeerStore extends PeerStore[Id] {
  private val whitelist = mutable.Map[PeerId, PeerInfo]()

  private val blacklist = mutable.Map[PeerId, PeerInfo]()

  override def get(id: PeerId): Id[Option[PeerInfo]] =
    whitelist.get(id)

  override def put(peer: PeerInfo): Id[Unit] =
    whitelist.put(peer.id, peer)

  override def del(id: PeerId): Id[Unit] =
    whitelist.remove(id)

  override def has(id: PeerId): Id[Boolean] =
    whitelist.contains(id)

  override def ids: Id[List[PeerId]] =
    whitelist.keys.toList

  override def clear(): Id[Unit] = {
    whitelist.clear()
    blacklist.clear()
  }

  override def banPeer(peer: PeerInfo): Id[Unit] = {
    if (has(peer.id)) {
      del(peer.id)
    }
    blacklist.put(peer.id, peer)
  }

  override def isBanned(id: PeerId): Id[Boolean] =
    blacklist.contains(id)
}
