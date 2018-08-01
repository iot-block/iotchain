package jbok.p2p

import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.async.Ref
import fs2.{Scheduler, _}
import jbok.network.NetAddress
import jbok.persistent.{KeyValueDB, KeyValueStore}
import scodec.bits.ByteVector
import jbok.codec.rlp.codecs._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

class KnownAddrStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, String, Set[NetAddress]](ByteVector.empty, db) {
  private val key = "KnownAddrs"

  def getKnownAddrs: F[Set[NetAddress]] = getOpt(key).map(_.getOrElse(Set.empty))

  def updateKnownAddrs(toAdd: Set[NetAddress], toRemove: Set[NetAddress]): F[Unit] =
    for {
      known <- getKnownAddrs
      updated = known ++ toAdd -- toRemove
      _ <- put(key, updated)
    } yield ()
}

case class KnownAddrManagerConfig(persistInterval: FiniteDuration = 60.seconds, maxPersistedAddrs: Int = 1024)

class KnownAddrManager[F[_]](
    known: Ref[F, Set[NetAddress]],
    toAdd: Ref[F, Set[NetAddress]],
    toRemove: Ref[F, Set[NetAddress]],
    config: KnownAddrManagerConfig,
    store: KnownAddrStore[F]
)(implicit F: Effect[F], S: Scheduler, EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger

  def addKnownAddr(addr: NetAddress): F[Unit] =
    for {
      s <- known.get
      _ <- if (s.contains(addr)) { F.unit } else {
        known.modify(_ + addr) *> toAdd.modify(_ + addr) *> toRemove.modify(_ + addr)
      }
    } yield ()

  def removeKnownAddr(addr: NetAddress): F[Unit] =
    for {
      s <- known.get
      _ <- if (!s.contains(addr)) {
        F.unit
      } else {
        known.modify(_ - addr) *> toAdd.modify(_ - addr) *> toRemove.modify(_ + addr)
      }
    } yield ()

  def getKnownAddrs: F[Set[NetAddress]] =
    known.get

  def persist: F[Unit] =
    for {
      s <- known.get
      a <- toAdd.get
      r <- toRemove.get
      (ta, tr) = if (s.size > config.maxPersistedAddrs) {
        val toAbandon = s.take(s.size - config.maxPersistedAddrs)
        val ta = a -- toAbandon
        val tr = r ++ toAbandon
        (ta, tr)
      } else {
        (a, r)
      }
      _ <- if (ta.nonEmpty || tr.nonEmpty) {
        store.updateKnownAddrs(ta, tr) *> toAdd.modify(_ => Set.empty) *> toRemove.modify(_ => Set.empty)
      } else {
        F.unit
      }
    } yield ()

  def stream: Stream[F, Unit] =
    S.awakeEvery[F](config.persistInterval).evalMap(_ => persist)
}

object KnownAddrManager {
  def apply[F[_]](db: KeyValueDB[F], config: KnownAddrManagerConfig)(
      implicit F: Effect[F],
      S: Scheduler,
      EC: ExecutionContext
  ): F[KnownAddrManager[F]] =
    for {
      known <- fs2.async.refOf[F, Set[NetAddress]](Set.empty)
      toAdd <- fs2.async.refOf[F, Set[NetAddress]](Set.empty)
      toRemove <- fs2.async.refOf[F, Set[NetAddress]](Set.empty)
      store = new KnownAddrStore[F](db)
    } yield new KnownAddrManager[F](known, toAdd, toRemove, config, store)
}
