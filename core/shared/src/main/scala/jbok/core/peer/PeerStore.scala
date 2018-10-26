package jbok.core.peer
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.codecs._
import jbok.core.store.Namespaces
import jbok.persistent.{KeyValueDB, KeyValueStore}
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration

class PeerStore[F[_]](db: KeyValueDB[F])(implicit F: Sync[F])
  extends KeyValueStore[F, String, ByteVector](Namespaces.NodeNamespace, db) {

  def lastPingKey(id: ByteVector) = s"n:${id.toHex}:lastping"

  def lastPongKey(id: ByteVector) = s"n:${id.toHex}:lastping"

  def failsKey(id: ByteVector) = s"n:${id.toHex}:fails"

  def nodeKey(id: ByteVector) = s"n:${id.toHex}"

  def getLastPingReceived(id: ByteVector): F[Long] =
    for {
      opt <- getOpt(lastPingKey(id))
      ts <- opt.fold(F.pure(0L))(decode[Long])
    } yield ts

  def putLastPingReceived(id: ByteVector, ts: Long): F[Unit] =
    encode(ts).flatMap(bytes => put(lastPingKey(id), bytes))

  def getLastPongReceived(id: ByteVector): F[Long] =
    for {
      opt <- getOpt(lastPongKey(id))
      ts <- opt.fold(F.pure(0L))(decode[Long])
    } yield ts

  def putLastPongReceived(id: ByteVector, ts: Long): F[Unit] =
    encode(ts).flatMap(bytes => put(lastPongKey(id), bytes))

  def getFails(id: ByteVector): F[Int] =
    for {
      opt <- getOpt(failsKey(id))
      fails <- opt.fold(F.pure(0))(decode[Int])
    } yield fails

  def putFails(id: ByteVector, fails: Int): F[Unit] =
    encode(fails).flatMap(bytes => put(failsKey(id), bytes))

  def getSeeds(n: Int, maxAge: FiniteDuration): F[List[PeerNode]] = ???

  def getNodeOpt(id: ByteVector): F[Option[PeerNode]] =
      OptionT(getOpt(nodeKey(id))).semiflatMap(decode[PeerNode]).value

  def delNode(id: ByteVector): F[Unit] =
    del(nodeKey(id))
}
