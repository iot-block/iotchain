package jbok.core.peer

import cats.effect.Concurrent
import cats.implicits._
import fs2._
import fs2.concurrent.Queue
import jbok.core.store.ColumnFamilies
import jbok.persistent.{KVStore, SingleColumnKVStore}

final class PeerStore[F[_]](store: SingleColumnKVStore[F, String, PeerUri], queue: Queue[F, PeerUri])(implicit F: Concurrent[F]) {
  def get(uri: String): F[Option[PeerUri]] =
    store.get(uri)

  def put(uri: PeerUri): F[Unit] =
    store.put(uri.uri, uri) >> queue.enqueue1(uri)

  def add(uris: PeerUri*): F[Unit] =
    uris.toList.traverse_(put)

  def del(uri: String): F[Unit] =
    store.del(uri)

  def getAll: F[List[PeerUri]] =
    store.toMap.map(_.values.toList)

  def subscribe: Stream[F, PeerUri] =
    queue.dequeue
}

object PeerStore {
  def apply[F[_]](db: KVStore[F])(implicit F: Concurrent[F]): F[PeerStore[F]] =
    for {
      queue <- Queue.circularBuffer[F, PeerUri](1000)
      store = SingleColumnKVStore[F, String, PeerUri](ColumnFamilies.Peer, db)
    } yield new PeerStore[F](store, queue)
}
