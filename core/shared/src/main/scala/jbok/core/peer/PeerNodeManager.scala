package jbok.core.peer

import java.net.URI

import cats.effect.Effect
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.store.PeerNodeStore
import jbok.persistent.KeyValueDB

trait PeerNodeManager[F[_]] {
  def add(uris: URI*): F[Unit]

  def remove(uris: URI*): F[Unit]

  def getAll: F[Set[PeerNode]]
}

object PeerNodeManager {
  def apply[F[_]](db: KeyValueDB[F], maxPersisted: Int = 1024)(implicit F: Effect[F]): F[PeerNodeManager[F]] = {
    val store = new PeerNodeStore[F](db)
    for {
      init  <- store.get
      known <- Ref.of[F, Set[PeerNode]](init)
    } yield new PeerNodeManager[F] {
        override def add(uris: URI*): F[Unit] =
          for {
            cur <- known.get
            now = (cur ++ uris.map(PeerNode.fromUri)).take(maxPersisted)
            _ <- if (cur == now) {
              F.unit
            } else {
              known.set(now) *> store.put(now)
            }
          } yield ()

        override def remove(uris: URI*): F[Unit] =
          for {
            cur <- known.get
            now = cur -- uris.map(PeerNode.fromUri).toSet
            _ <- if (cur == now) {
              F.unit
            } else {
              known.set(now) *> store.put(now)
            }
          } yield ()

        override def getAll: F[Set[PeerNode]] =
          known.get
      }
  }
}
