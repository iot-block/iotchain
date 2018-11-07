package jbok.core.peer
import cats.effect.Sync
import jbok.core.store.namespaces
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration
import jbok.codec.rlp.implicits._

class PeerStore[F[_]](db: KeyValueDB[F])(implicit F: Sync[F]) {
  val ns = namespaces.Peer

  def lastPingKey(id: ByteVector) = s"n:${id.toHex}:lastping"

  def lastPongKey(id: ByteVector) = s"n:${id.toHex}:lastping"

  def failsKey(id: ByteVector) = s"n:${id.toHex}:fails"

  def nodeKey(id: ByteVector) = s"n:${id.toHex}"

  def getLastPingReceived(id: ByteVector): F[Long] =
    db.getOptT[String, Long](lastPingKey(id), ns).getOrElse(0L)

  def putLastPingReceived(id: ByteVector, ts: Long): F[Unit] =
    db.put(lastPingKey(id), ts, ns)

  def getLastPongReceived(id: ByteVector): F[Long] =
    db.getOptT[String, Long](lastPongKey(id), ns).getOrElse(0)

  def putLastPongReceived(id: ByteVector, ts: Long): F[Unit] =
    db.put(lastPongKey(id), ts, ns)

  def getFails(id: ByteVector): F[Int] =
    db.getOptT[String, Int](failsKey(id), ns).getOrElse(0)

  def putFails(id: ByteVector, fails: Int): F[Unit] =
    db.put(failsKey(id), fails, ns)

  def getSeeds(n: Int, maxAge: FiniteDuration): F[List[PeerNode]] = ???

  def getNodeOpt(id: ByteVector): F[Option[PeerNode]] =
    db.getOpt[String, PeerNode](nodeKey(id), ns)

  def delNode(id: ByteVector): F[Unit] =
    db.del[String](nodeKey(id), ns)
}
