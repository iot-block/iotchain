package jbok.examples.hg

import cats.Monad
import cats.implicits._
import jbok.crypto.hashing.{HashType, MultiHash}
import scodec.bits.BitVector

import scala.annotation.tailrec
import scala.collection.mutable

case class HGConfig(
    n: Int, // the number of members in the population
    c: Int = 10, // frequency of coin rounds (such as c = 10)
    d: Int = 1 // rounds delayed before start of election (such as d = 1)
)

class HG[F[_]: Monad](config: HGConfig)(implicit pool: Pool[F]) {
  import Event._

  private var topologicalIndex = 0

  val superMajority: Int = 2 * config.n / 3

  def undividedEvents: F[List[Event]] = pool.getEvents(-1, !_.isDivided).map(_.sortBy(_.info.topologicalIndex))

  def undecidedRounds: F[List[Round]] = pool.getRounds(!_.isDecided)

  def unorderedRounds: F[List[Round]] = pool.getRounds(!_.isOrdered)

  def event(hash: MultiHash): F[Event] = pool.getEvent(hash)

  def eventsAt(r: Round): F[List[Event]] = pool.getEvents(r)

  def witnessesAt(r: Round): F[List[Event]] = pool.getEvents(r, _.isWitness)

  def famousWitnessesAt(r: Round): F[List[Event]] = pool.getEvents(r, _.isFamous == Some(true))

  def unorderedEventsAt(r: Round): F[List[Event]] = pool.getEvents(r, !_.isOrdered)

  /**
    * @param x event
    * @return the max of {selfParentRound, otherParentRound}
    */
  def parentRound(x: Event): F[ParentRoundInfo] = {
    for {
      spr <- selfParentRound(x)
      opr <- otherParentRound(x)
    } yield {
      if (spr.round >= opr.round) spr else opr
    }
  }

  def selfParentRound(x: Event): F[ParentRoundInfo] = {
    if (x.sp == HG.genesis.hash) {
      ParentRoundInfo(0, isRoot = true).pure[F]
    } else {
      pool.getEvent(x.sp).map(spe => ParentRoundInfo(spe.info.round, isRoot = false))
    }
  }

  def otherParentRound(x: Event): F[ParentRoundInfo] = {
    if (x.op == HG.genesis.hash) {
      ParentRoundInfo(0, isRoot = true).pure[F]
    } else {
      pool.getEvent(x.op).map(ope => ParentRoundInfo(ope.info.round, isRoot = false))
    }
  }

  /**
    * @param x event
    * @return true if round of x should be incremented by 1
    * S is the witness events at parentRound(x)
    * ∃S ⊆ E , manyCreators(S) ∧ (∀y ∈ S, round(y) = parentRound(x) ∧ stronglySee(x, y))
    */
  def roundInc(pr: ParentRoundInfo, x: Event): F[Boolean] = {
    for {
      inc <- if (pr.isRoot) {
        true.pure[F]
      } else {
        for {
          roundWits <- witnessesAt(pr.round)
          ss = roundWits.map(w => stronglySee(x, w, superMajority))
        } yield ss.count(_ == true) > superMajority
      }
    } yield inc
  }

  def divideRounds(events: List[Event]): F[List[Event]] = events.traverse(divideEvent)

  def divideEvent(x: Event): F[Event] = {
    for {
      sp <- pool.getEvent(x.sp)
      pri <- parentRound(x)
      inc <- roundInc(pri, x)
      isWitness = inc || pri.round > sp.info.round
      divided = if (inc) {
        x.divided(pri.round + 1, isWitness = isWitness)
      } else {
        val isFamous = if (isWitness) None else Some(false)
        x.divided(pri.round, isWitness = isWitness, isFamous = isFamous)
      }
      _ <- pool.putEvent(divided)
    } yield divided
  }

  type Votes = mutable.Map[MultiHash, Boolean]

  /**
    * @param x witness event
    * @param round starting round
    * @param lastRound last round
    * @param votes accumulated votes for x
    * @return decided or not
    */
  @tailrec
  final def decideWitnessFame(
      x: Event,
      round: Round,
      lastRound: Round,
      witsMap: Map[Int, List[Event]],
      votes: Votes = mutable.Map()
  ): Event = {
    @tailrec
    def decideFameByRound(
        x: Event,
        ys: List[Event],
        witnesses: List[Event],
    ): Event = {
      if (x.isDecided || ys.isEmpty) {
        x
      } else {
        val decided = vote(x, ys.head, witnesses, votes)
        decideFameByRound(decided, ys.tail, witnesses)
      }
    }

    if (x.isDecided || round > lastRound) {
      x
    } else {
      val ys = witsMap(round)
      val prevWitnesses = witsMap(round - 1)
      val decided = decideFameByRound(x, ys, prevWitnesses)
      decideWitnessFame(decided, round + 1, lastRound, witsMap, votes)
    }
  }

  /**
    * @param x event
    * @param y event
    * @param witnesses witness events at round (y.round - 1)
    * @param votes accumulated votes for x
    * @return
    */
  def vote(
      x: Event,
      y: Event,
      witnesses: List[Event],
      votes: Votes
  ): Event = {
    if (x.isDecided) {
      x
    } else if (y.info.round - x.info.round == config.d) {
      // direct voting
      votes += y.hash -> see(y, x)
      x
    } else {
      val ssByY = witnesses.filter(w => stronglySee(y, w, superMajority))
      val (yays, nays) = ssByY.foldLeft((0, 0))((acc, cur) => {
        if (votes(cur.hash))
          (acc._1 + 1, acc._2)
        else
          (acc._1, acc._2 + 1)
      })

      val vote = yays >= nays
      val majority = math.max(yays, nays)

      if (y.info.round - x.info.round % config.c != 0) {
        // normal round
        if (majority > superMajority) {
          // decided
          val decided = x.decided(vote)
          votes += y.hash -> vote
          decided
        } else {
          votes += y.hash -> vote
          x
        }
      } else {
        // coin round
        if (majority > superMajority) {
          // still follow superMajority
          votes += y.hash -> vote
          x
        } else {
          // random bit
          val randomVote = HG.middleBit(y.hash.digest.toArray)
          votes += y.hash -> randomVote
          x
        }
      }
    }
  }

  def decideFame(start: Round, end: Round): F[List[Event]] = {
    for {
      witsList <- (start to end).toList.traverse(r => witnessesAt(r).map(r -> _))
      witsMap = witsList.toMap
      decided = witsList.flatMap(_._2.map(w => decideWitnessFame(w, w.info.round + 1, end, witsMap)))
      _ <- decided.traverse(pool.putEvent)
    } yield decided
  }

  def medianTimestamp(hashes: List[MultiHash]): F[Long] = {
    require(hashes.nonEmpty)
    for {
      events <- hashes.traverse(event)
    } yield HG.median(events.map(_.body.timestamp))
  }

  def findOrder(start: Round, end: Round): F[List[Event]] = {
    @tailrec
    def findEventOrder(x: Event, round: Round, fwsMap: Map[Round, List[Event]]): F[Event] = {
      if (x.isOrdered || round > end) {
        x.pure[F]
      } else {
        val fws = fwsMap(round)
        val sees = fws.filter(w => see(w, x))
        if (sees.length > fws.length / 2) {
          val hashes = sees.flatMap(w => Event.earliestSelfAncestorSee(w, x))
          for {
            ts <- medianTimestamp(hashes)
          } yield x.ordered(round, ts)
        } else {
          findEventOrder(x, round + 1, fwsMap)
        }
      }
    }

    for {
      fws <- (start + 1 to end).toList.traverse(i => famousWitnessesAt(i).map(i -> _))
      fwsMap = fws.toMap
      unordered <- (start to end).toList.flatTraverse(eventsAt)
      ordered <- unordered.traverse(x => findEventOrder(x, x.round + 1, fwsMap))
      _ <- ordered.traverse(pool.putEvent)
    } yield ordered
  }

  def updateLastAncestors(event: Event): F[Event] = {
    if (event.hash == HG.genesis.hash) {
      event.pure[F]
    } else {
      for {
        sp <- pool.getEvent(event.body.selfParent)
        op <- pool.getEvent(event.body.otherParent)
      } yield {
        val self = Map(event.body.creator -> (EventCoordinates(event.hash, event.body.index) :: Nil))
        val lastAncestors = self |+| sp.info.lastAncestors.mapValues(_ :: Nil) |+| op.info.lastAncestors
          .mapValues(_ :: Nil)
        val r = lastAncestors.mapValues(_.maxBy(_.index))
        event.updateLastAncestors(r)
      }
    }
  }

  def updateFirstDescendants(event: Event): F[Unit] = {
    def update(ancestor: Option[Event]): F[Unit] = {
      if (ancestor.isEmpty || ancestor.get.info.firstDescendants.contains(event.body.creator)) {
        ().pure[F]
      } else {
        val x = ancestor.get
        val updated = x.updateFirstDescendant(event.body.creator, EventCoordinates(event.hash, event.body.index))

        for {
          _ <- pool.putEvent(updated)
          sp <- pool.getEventOpt(updated.sp)
          op <- pool.getEventOpt(updated.op)
          _ <- update(sp)
          _ <- update(op)
        } yield ()
      }
    }

    for {
      ancestors <- event.info.lastAncestors.toList.traverse(t => pool.getEvent(t._2.hash))
      _ <- ancestors.traverse(x => update(Some(x)))
    } yield ()
  }

  def insertEvent(event: Event): F[Unit] = {
    event.info.topologicalIndex = this.topologicalIndex
    this.topologicalIndex += 1
    for {
      updated <- updateLastAncestors(event)
      _ <- pool.putEvent(updated)
      _ <- updateFirstDescendants(updated)
    } yield ()
  }
}

object HG {
  def median[A: Ordering](xs: Seq[A]): A = {
    require(xs.nonEmpty)
    xs.sorted(implicitly[Ordering[A]])(xs.length / 2)
  }

  def middleBit(bytes: Array[Byte]): Boolean = {
    val bv = BitVector(bytes)
    bv(bv.length / 2)
  }

  val genesis: Event = {
    val nil = MultiHash.hash("", HashType.sha256)
    val god = MultiHash.hash("god", HashType.sha256)
    val body = EventBody(nil, nil, god, 0L, 0, Nil)
    val hash = MultiHash.hash(body, HashType.sha256)
    Event(body, hash)(
      EventInfo(
        topologicalIndex = 0,
        round = 0,
        isWitness = true,
        isFamous = Some(true),
        roundReceived = 1
      )
    )
  }
}
