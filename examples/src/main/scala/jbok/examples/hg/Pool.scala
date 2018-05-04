package jbok.examples.hg

import cats.Monad
import cats.implicits._
import jbok.core.store.KVStore
import jbok.crypto.hashing.MultiHash

class Pool[F[_]: Monad](
    events: KVStore[F, MultiHash, Event],
    rounds: KVStore[F, Round, Map[MultiHash, RoundInfo]],
    roots: KVStore[F, MultiHash, Root],
    tip: KVStore[F, MultiHash, MultiHash]
) {
  def getEvent(hash: MultiHash): F[Event] = events.get(hash)

  def getEventOpt(hash: MultiHash): F[Option[Event]] = events.getOpt(hash)

  def putEvent(event: Event): F[Unit] = events.put(event.hash, event)

  def getEventsAt(r: Round, eventsFilter: RoundInfo => Boolean): F[List[Event]] =
    for {
      hashes <- rounds.get(r).map(_.filter { case (_, ri) => eventsFilter(ri) }.keys.toList)
      xs <- hashes.traverse(getEvent)
    } yield xs

  def getWitnessesAt(r: Round): F[List[Event]] = getEventsAt(r, _.isWitness)

  def getFamousWitnessAt(r: Round): F[List[Event]] = getEventsAt(r, _.isFamous.contains(true))

  def getRoot(hash: MultiHash): F[Root] = roots.get(hash)

  def putRoot(hash: MultiHash, root: Root): F[Unit] = roots.put(hash, root)

  def lastEventFrom(creator: MultiHash): F[Option[MultiHash]] =
    tip.getOpt(creator)

  def undividedEvents: F[List[Event]] = ???

  def undividedEventsAt(r: Round): F[List[Event]] = getEventsAt(r, _.isFamous.isDefined)

  def getRoundInfo(r: Round): F[Map[MultiHash, RoundInfo]] =
    rounds.get(r)

  def putRoundInfo(event: Event): F[Unit] = {
    require(event.isDivided)
    for {
      roundInfo <- getRoundInfo(event.round)
      _ <- rounds.put(event.round, roundInfo ++ Map(event.hash -> RoundInfo(event.hash, event.round, event.isWitness)))
    } yield ()
  }

  def undecidedRounds: F[List[Round]] = ???

  def lastRound: F[Round] = rounds.keys.map(_.max)
}
