package jbok.core.peer

import java.net.URI

import cats.effect.Effect
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.store.namespaces
import jbok.persistent.KeyValueDB
import jbok.codec.rlp.implicits._

trait PeerNodeManager[F[_]] {
  def add(uris: URI*): F[Unit]

  def remove(uris: URI*): F[Unit]

  def getAll: F[Set[PeerNode]]
}

object PeerNodeManager {
  val key = "KnownAddrs"

  def apply[F[_]](db: KeyValueDB[F], maxPersisted: Int = 1024)(implicit F: Effect[F]): F[PeerNodeManager[F]] =
    for {
      init  <- db.getOpt[String, Set[PeerNode]](key, namespaces.Peer).map(_.getOrElse(Set.empty))
      known <- Ref.of[F, Set[PeerNode]](init)
    } yield
      new PeerNodeManager[F] {
        private def put(now: Set[PeerNode]): F[Unit] =
          db.put(key, now, namespaces.Peer)

        override def add(uris: URI*): F[Unit] =
          for {
            cur <- known.get
            now = (cur ++ uris.map(PeerNode.fromUri)).take(maxPersisted)
            _ <- if (cur == now) {
              F.unit
            } else {
              known.set(now) *> put(now)
            }
          } yield ()

        override def remove(uris: URI*): F[Unit] =
          for {
            cur <- known.get
            now = cur -- uris.map(PeerNode.fromUri).toSet
            _ <- if (cur == now) {
              F.unit
            } else {
              known.set(now) *> put(now)
            }
          } yield ()

        override def getAll: F[Set[PeerNode]] =
          known.get
      }
}
