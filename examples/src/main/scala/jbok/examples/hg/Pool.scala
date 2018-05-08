package jbok.examples.hg

import cats.Monad
import cats.implicits._
import jbok.core.store.KVStore
import jbok.crypto.hashing.MultiHash

import scala.collection.mutable

class Pool[F[_]: Monad](
    events: KVStore[F, MultiHash, Event],
    rounds: KVStore[F, Round, RoundInfo]
) {
  private val undividedEvents = mutable.Set[MultiHash]()

  def getEvent(hash: MultiHash): F[Event] = events.get(hash)

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

  def getEventsAt(r: Round, eventsFilter: EventInfo => Boolean = _ => true): F[List[Event]] = {
    for {
      hashes <- rounds.get(r).map(_.events.filter { case (_, ei) => eventsFilter(ei) }.keys.toList)
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

  def putRoundInfo(event: Event): F[Unit] = {
    require(event.isDivided)
    for {
      roundInfo <- rounds.getOpt(event.round).map(_.getOrElse(RoundInfo(event.round)))
      _ <- rounds.put(event.round, roundInfo.update(event))
    } yield ()
  }

  def getRounds(f: RoundInfo => Boolean): F[List[Round]] = {
    for {
      rs <- rounds.keys
      ri <- rs.traverse(rounds.get)
    } yield ri.filter(f).map(_.round).sorted
  }

  def undecidedRounds: F[List[Round]] = getRounds(!_.isDecided)

  def unorderedRounds: F[List[Round]] = getRounds(!_.isOrdered)

  def lastRound: F[Round] = rounds.keys.map(_.max)
}
