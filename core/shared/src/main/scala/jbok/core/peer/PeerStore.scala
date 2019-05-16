package jbok.core.peer

import cats.effect.Concurrent
import cats.implicits._
import fs2._
import fs2.concurrent.Queue
import jbok.codec.rlp.implicits._
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

final class PeerStore[F[_]](db: KeyValueDB[F], queue: Queue[F, PeerUri])(implicit F: Concurrent[F]) {
  val namespace = ByteVector("peer".getBytes)

  def get(uri: String): F[PeerUri] =
    db.get[String, PeerUri](uri, namespace)

  def put(uri: PeerUri): F[Unit] =
    db.put[String, PeerUri](uri.uri.toString, uri, namespace) >> queue.enqueue1(uri)

  def add(uris: PeerUri*): F[Unit] =
    uris.toList.traverse_(put)

  def del(uri: String): F[Unit] =
    db.del[String](uri, namespace)

  def getAll: F[List[PeerUri]] =
    db.toMap[String, PeerUri](namespace).map(_.values.toList)

  def subscribe: Stream[F, PeerUri] =
    queue.dequeue
}

object PeerStore {
  def apply[F[_]](db: KeyValueDB[F])(implicit F: Concurrent[F]): F[PeerStore[F]] =
    Queue.bounded[F, PeerUri](100).map(queue => new PeerStore[F](db, queue))
}
