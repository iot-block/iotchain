package jbok.examples.hg

import cats.effect.Sync
import cats.implicits._
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

abstract class EventPool[F[_]: Sync] {
  def getEventByHash(hash: ByteVector): F[Event]

  def getEventByHashOpt(hash: ByteVector): F[Option[Event]]

  def putEvent(event: Event): F[Unit]

  def getRounds(f: RoundInfo => Boolean): F[List[Int]]

  def getEvents(r: Int, eventFilter: EventInfo => Boolean = _ => true): F[List[Event]]

  def putRoundInfo(event: Event): F[Unit]

  def lastRound: F[Int]

  /////////////////////////
  /////////////////////////

  def getUndividedEvents: F[List[Event]] =
    getEvents(-1, !_.isDivided).map(_.sortBy(_.info.topologicalIndex))

  def getUndecidedRounds: F[List[Int]] =
    getRounds(!_.isDecided)

  def getUnorderedRounds: F[List[Int]] =
    getRounds(!_.isOrdered)

  def getWitnesses(r: Int): F[List[Event]] =
    getEvents(r, _.isWitness)

  def getFamousWitnesses(r: Int): F[List[Event]] =
    getEvents(r, _.isFamous == Some(true))

  def getUnorderedEvents(r: Int): F[List[Event]] =
    getEvents(r, !_.isOrdered)

  def getParentRound(x: Event): F[ParentRoundInfo] =
    for {
      spr <- getSelfParentRound(x)
      opr <- getOtherParentRound(x)
    } yield {
      if (spr.round >= opr.round) spr else opr
    }

  def getSelfParentRound(x: Event): F[ParentRoundInfo] =
    if (x.sp == Consensus.genesis.hash) {
      ParentRoundInfo(0, isRoot = true).pure[F]
    } else {
      getEventByHash(x.sp).map(spe => ParentRoundInfo(spe.info.round, isRoot = false))
    }

  def getOtherParentRound(x: Event): F[ParentRoundInfo] =
    if (x.op == Consensus.genesis.hash) {
      ParentRoundInfo(0, isRoot = true).pure[F]
    } else {
      getEventByHash(x.op).map(ope => ParentRoundInfo(ope.info.round, isRoot = false))
    }

  def updateLastAncestors(event: Event): F[Event] =
    if (event.hash == Consensus.genesis.hash) {
      event.pure[F]
    } else {
      for {
        sp <- getEventByHash(event.body.selfParent)
        op <- getEventByHash(event.body.otherParent)
      } yield {
        val self = Map(event.body.creator -> (EventCoordinates(event.hash, event.body.index) :: Nil))
        val lastAncestors = self |+| sp.info.lastAncestors.mapValues(_ :: Nil) |+| op.info.lastAncestors
          .mapValues(_ :: Nil)
        val r = lastAncestors.mapValues(_.maxBy(_.index))
        event.updateLastAncestors(r)
      }
    }

  def updateFirstDescendants(event: Event): F[Unit] = {
    def update(ancestor: Option[Event]): F[Unit] =
      if (ancestor.isEmpty || ancestor.get.info.firstDescendants.contains(event.body.creator)) {
        ().pure[F]
      } else {
        val x = ancestor.get
        val updated = x.updateFirstDescendant(event.body.creator, EventCoordinates(event.hash, event.body.index))

        for {
          _ <- putEvent(updated)
          sp <- getEventByHashOpt(updated.sp)
//          op <- getEventByHashOpt(updated.op)
          _ <- update(sp)
//          _ <- update(op)
        } yield ()
      }

    for {
      ancestors <- event.info.lastAncestors.toList.traverse(t => getEventByHash(t._2.hash))
      _ <- ancestors.traverse(x => update(Some(x)))
    } yield ()
  }
}

object EventPool {
  def apply[F[_]](implicit F: Sync[F]): F[EventPool[F]] =
    for {
      events <- fs2.async.refOf[F, Map[ByteVector, Event]](Map.empty)
      rounds <- fs2.async.refOf[F, Map[Int, RoundInfo]](Map.empty)
    } yield new EventPool[F] {
      private[this] val log = org.log4s.getLogger

      override def getEventByHash(hash: ByteVector): F[Event] =
        events.get.map(_.apply(hash))

      override def getEventByHashOpt(hash: ByteVector): F[Option[Event]] =
        events.get.map(_.get(hash))

      override def putEvent(event: Event): F[Unit] =
        for {
          _ <- putRoundInfo(event)
          _ = log.info(s"put round info")
          _ <- events.modify(_ + (event.hash -> event))
          _ = log.info(s"put event done")
        } yield ()

      override def getRounds(f: RoundInfo => Boolean): F[List[Int]] =
        for {
          rs <- rounds.get
        } yield rs.filter { case (r, ri) => f(ri) }.keys.toList.sorted

      override def getEvents(r: Int, eventFilter: EventInfo => Boolean): F[List[Event]] =
        for {
          rs <- rounds.get
          hashes = rs(r).events.filter { case (_, ei) => eventFilter(ei) }.keys.toList
          xs <- hashes.traverse(getEventByHash)
        } yield xs

      override def putRoundInfo(event: Event): F[Unit] = {
        val update = for {
          roundInfo <- rounds.get.map(_.get(event.round)).map(_.getOrElse(RoundInfo(event.round)))
          _ <- rounds.modify(_ + (event.round -> roundInfo.+=(event)))
        } yield ()

        val invalidate = for {
          ri <- rounds.get.map(_.get(-1)).map(_.getOrElse(RoundInfo(-1)))
          _ <- if (event.isDivided) rounds.modify(_ + (-1 -> ri.-=(event.hash))) else ().pure[F]
        } yield ()

        invalidate *> update
      }

      override def lastRound: F[Int] =
        rounds.get.map(_.keys.max)
    }
}
