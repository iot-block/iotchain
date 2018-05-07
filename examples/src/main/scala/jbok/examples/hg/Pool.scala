package jbok.examples.hg

import cats.Monad
import cats.implicits._
import jbok.core.store.KVStore
import jbok.crypto.hashing.MultiHash

import scala.collection.mutable

class Pool[F[_]: Monad](
    events: KVStore[F, MultiHash, Event],
    rounds: KVStore[F, Round, Map[MultiHash, EventInfo]]
) {
  private val undividedEvents = mutable.Set[MultiHash]()

  def getEvent(hash: MultiHash): F[Event] = events.get(hash)

  def allEvents: F[List[Event]] = {
    for {
      keys <- events.keys
      es <- keys.traverse(events.get)
    } yield es
  }

  def getEventOpt(hash: MultiHash): F[Option[Event]] = events.getOpt(hash)

  def putEvent(event: Event): F[Unit] = {
    if (!event.isDivided) {
      undividedEvents += event.hash
      for {
        _ <- events.put(event.hash, event)
      } yield ()
    } else {
      undividedEvents -= event.hash
      for {
        _ <- putRoundInfo(event)
        _ <- events.put(event.hash, event)
      } yield ()
    }
  }

  def getEventsAt(r: Round, eventsFilter: EventInfo => Boolean): F[List[Event]] = {
    for {
      hashes <- getRoundInfo(r).map(_.filter { case (_, ri) => eventsFilter(ri) }.keys.toList)
      xs <- hashes.traverse(getEvent)
    } yield xs
  }

  def getWitnessesAt(r: Round): F[List[Event]] = getEventsAt(r, _.isWitness)

  def getFamousWitnessAt(r: Round): F[List[Event]] = getEventsAt(r, _.isFamous.contains(true))

  def getUndividedEvents: F[List[Event]] = {
    undividedEvents.toList
      .traverse(h => getEvent(h))
      .map(_.sortBy(_.topologicalIndex))
  }

  def getUndividedEventsAt(r: Round): F[List[Event]] = getEventsAt(r, _.isFamous.isDefined)

  private def getRoundInfo(r: Round): F[Map[MultiHash, EventInfo]] = {
    for {
      ri <- rounds.getOpt(r)
    } yield ri.getOrElse(Map.empty)
  }

  private def putRoundInfo(event: Event): F[Unit] = {
    require(event.isDivided)
    for {
      roundInfo <- getRoundInfo(event.round)
      _ <- rounds.put(
        event.round,
        roundInfo + (event.hash -> EventInfo(
          event.hash,
          event.round,
          event.isWitness,
          event.isFamous,
          event.isOrdered)))
    } yield ()
  }

  def undecidedRounds: F[List[Round]] =
    for {
      rs <- rounds.keys
      ri <- rs.traverse(rounds.get)
    } yield ri.filter(_.exists(_._2.isFamous.isEmpty)).map(_.head._2.round).sorted

  def lastRound: F[Round] = rounds.keys.map(_.max)
}
