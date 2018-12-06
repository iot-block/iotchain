package jbok.core.peer

import cats.effect.Concurrent
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration

object PeerStorePlatform {
  def fromKV[F[_]](db: KeyValueDB[F])(implicit F: Concurrent[F]): PeerStore[F] =
    new PeerStore[F] {
      val PingKey = ByteVector("ping".getBytes)
      val PongKey = ByteVector("pong".getBytes)
      val FailKey = ByteVector("fail".getBytes)
      val NodeKey = ByteVector("node".getBytes)

      override def getLastPing(id: NodeId): F[Long] =
        db.getOptT[ByteVector, Long](id, PingKey).getOrElse(0)

      override def putLastPing(id: NodeId, ts: Long): F[Unit] =
        db.put(id, ts, PingKey)

      override def getLastPong(id: NodeId): F[Long] =
        db.getOptT[ByteVector, Long](id, PongKey).getOrElse(0)

      override def putLastPong(id: NodeId, ts: Long): F[Unit] =
        db.put(id, ts, PongKey)

      override def getFails(id: NodeId): F[Int] =
        db.getOptT[ByteVector, Int](id, FailKey).getOrElse(0)

      override def putFails(id: NodeId, n: Int): F[Unit] =
        db.put(id, n, FailKey)

      override def getNodeOpt(id: NodeId): F[Option[PeerNode]] =
        db.getOpt[ByteVector, PeerNode](id, NodeKey)

      override def putNode(node: PeerNode): F[Unit] =
        db.put(node.id, node, NodeKey)

      override def delNode(id: NodeId): F[Unit] =
        db.del(id, NodeKey)

      override def getSeeds(n: Int, age: FiniteDuration): F[List[PeerNode]] = ???

      override def getNodes: F[List[PeerNode]] =
        db.toMap[ByteVector, PeerNode](NodeKey).map(_.values.toList)
    }
}
