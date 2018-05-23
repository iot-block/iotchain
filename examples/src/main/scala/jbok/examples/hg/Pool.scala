package jbok.examples.hg

import cats.Monad
import cats.implicits._
import jbok.core.store.KVStore
import jbok.crypto.hashing.Hash

class Pool[F[_]: Monad](val events: KVStore[F, Hash, Event], val rounds: KVStore[F, Round, RoundInfo]) {
  def getEvent(hash: Hash): F[Event] = events.get(hash)

  def getEventOpt(hash: Hash): F[Option[Event]] = events.getOpt(hash)

  def putEvent(event: Event): F[Unit] = {
    for {
      _ <- putRoundInfo(event)
      _ <- events.put(event.hash, event)
    } yield ()
  }

  def getRounds(f: RoundInfo => Boolean): F[List[Round]] = {
    for {
      rs <- rounds.keys
      ri <- rs.traverse(rounds.get)
    } yield ri.filter(f).map(_.round).sorted
  }

  def getEvents(r: Round, eventFilter: EventInfo => Boolean = _ => true): F[List[Event]] = {
    for {
      hashes <- rounds.get(r).map(_.events.filter { case (_, ei) => eventFilter(ei) }.keys.toList)
      xs <- hashes.traverse(getEvent)
    } yield xs
  }

  def putRoundInfo(event: Event): F[Unit] = {
    val update = for {
      roundInfo <- rounds.getOpt(event.round).map(_.getOrElse(RoundInfo(event.round)))
      _ <- rounds.put(event.round, roundInfo.+=(event))
    } yield ()

    val invalidate = for {
      ri <- rounds.getOpt(-1).map(_.getOrElse(RoundInfo(-1)))
      _ <- if (event.isDivided) rounds.put(-1, ri -= event.hash) else ().pure[F]
    } yield ()

    invalidate *> update
  }

  def lastRound: F[Round] = rounds.keys.map(_.max)
}
