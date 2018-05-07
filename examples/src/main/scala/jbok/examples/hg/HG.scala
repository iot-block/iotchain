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

abstract class HG[F[_]: Monad](
    val pool: Pool[F],
    val config: HGConfig
) {
  private var topologicalIndex = 0

  def superMajority(n: Int = config.n): Int = 2 * n / 3

  /**
    * @param x event
    * @param y event
    * @return true if y is an ancestor of x
    *
    * x == y ∨ ∃z ∈ parents(x), ancestor(z, y)
    */
  def ancestor(x: Event, y: Event): Boolean = {
    x == y || x.lastAncestorIndex(y.body.creator) >= y.body.index
  }

  /**
    * @param x event
    * @param y event
    * @return true if y is a selfAncestor of x
    *
    * x and y are created by a same creator
    * x == y ∨ (selfParent(x) ̸= ∅ ∧ selfAncestor(selfParent(x), y))
    */
  def selfAncestor(x: Event, y: Event): Boolean = {
    x == y || x.body.creator == y.body.creator && x.body.index >= y.body.index
  }

  /**
    * @param x event
    * @param y event
    * @return true if x sees y
    *
    */
  def see(x: Event, y: Event): Boolean = ancestor(x, y)

  /**
    * @param x event
    * @param y event
    * @return true if x strongly sees y
    *
    * see(x, y) ∧ (∃S ⊆ E, manyCreators(S) ∧(z∈S => (see(x,z)∧see(z,y))))
    */
  def stronglySee(x: Event, y: Event): Boolean = {
    val c = x.lastAncestors
      .map {
        case (creator, coord) => coord.index >= y.firstDescendants.get(creator).map(_.index).getOrElse(Int.MaxValue)
      }
      .count(_ == true)
    c > superMajority()
  }

  /**
    * @param x event
    * @param y event
    */
  def paths(x: Event, y: Event) = {
    val xs = x.lastAncestors
    val ys = y.firstDescendants

    val joined = for {
      (k, va) <- xs
      vb <- ys.get(k)
    } yield k -> (va, vb)
    joined
  }

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
    val sp = x.body.selfParent
    if (sp == HG.genesis.hash) {
      ParentRoundInfo(0, isRoot = true).pure[F]
    } else {
      pool.getEvent(sp).map(spe => ParentRoundInfo(spe.round, isRoot = false))
    }
  }

  def otherParentRound(x: Event): F[ParentRoundInfo] = {
    val op = x.body.otherParent
    if (op == HG.genesis.hash) {
      ParentRoundInfo(0, isRoot = true).pure[F]
    } else {
      pool.getEvent(op).map(ope => ParentRoundInfo(ope.round, isRoot = false))
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
        // If parent-round was obtained from a Root, then x is the Event that sits
        // right on top of the Root. RoundInc is true.
        true.pure[F]
      } else {
        // If parent-round was obtained from a regular Event, then we need to check
        // if x strongly-sees a strong majority of witnesses from parent-round.
        for {
          roundWits <- pool.getWitnessesAt(pr.round)
          ss = roundWits.map(w => stronglySee(x, w))
        } yield ss.count(_ == true) > superMajority()
      }
    } yield inc
  }

  def roundAndWitness(x: Event): F[Event] = {
    require(!x.isDivided)

    for {
      sp <- pool.getEvent(x.sp)
      pri <- parentRound(x)
      inc <- roundInc(pri, x)
    } yield {
      val isWitness = inc || pri.round > sp.round
      if (inc) {
        x.divided(pri.round + 1, isWitness = isWitness)
      } else {
        // not witness => not famous
        val isFamous = if (isWitness) None else Some(false)
        x.divided(pri.round, isWitness = isWitness, isFamous = isFamous)
      }
    }
  }

  /**
    * @param x event
    * @param y event
    * @return round(x) − round(y)
    */
  def diff(x: Event, y: Event): Round = {
    require(x.isDivided)
    require(y.isDivided)
    require(x.round >= y.round)
    x.round - y.round
  }

  /**
    *
    * @param events undivided events
    * @return divided events
    * As soon as an event x is known, it is assigned a round number x.round,
    * and the boolean value x.witness is calculated, indicating whether it is a “witness”,
    * i.e. the first event that a member created in that round.
    */
  def divideRounds(events: List[Event]): F[List[Event]] = {
    for {
      divided <- events.traverse(divide)
    } yield divided
  }

  /**
    * @param event Event
    * @return event with divided round and witness
    *
    */
  def divide(event: Event): F[Event] = {
    require(!event.isDivided)
    for {
      divided <- roundAndWitness(event)
      _ <- pool.putEvent(divided)
    } yield divided
  }

  /**
    * @param x witness event
    * @param round start round, must greater than x.round
    * @param lastRound the last round so far
    * @param votes accumulated votes for x
    * @return decided witness event
    */
  def decide(
      x: Event,
      round: Round,
      lastRound: Round,
      votes: mutable.Map[MultiHash, Boolean] = mutable.Map()
  ): F[Event] = {
    if (x.isDecided || round > lastRound) {
      x.pure[F]
    } else {
      for {
        ys <- pool.getWitnessesAt(round)
        witnesses <- pool.getWitnessesAt(round - 1)
        (decided, vs) = decideByRound(x, ys, witnesses, votes)
        d <- decide(decided, round + 1, lastRound, vs)
      } yield d
    }
  }

  @tailrec
  final def decideByRound(
      x: Event,
      ys: List[Event],
      witnesses: List[Event],
      votes: mutable.Map[MultiHash, Boolean]): (Event, mutable.Map[MultiHash, Boolean]) = {
    if (x.isDecided || ys.isEmpty) {
      (x, votes)
    } else {
      val (decided, vs) = decideByEvent(x, ys.head, witnesses, votes)
      decideByRound(decided, ys.tail, witnesses, vs)
    }
  }

  /**
    *
    * @param x event
    * @param y event
    * @param witnesses witness events at round (y.round - 1)
    * @param votes accumulated votes for x
    * @return
    */
  def decideByEvent(
      x: Event,
      y: Event,
      witnesses: List[Event],
      votes: mutable.Map[MultiHash, Boolean]): (Event, mutable.Map[MultiHash, Boolean]) = {
    if (x.isDecided) {
      (x, votes)
    } else if (y.round - x.round == config.d) {
      // direct voting
      (x, votes += y.hash -> see(y, x))
    } else {
      val ssByY = witnesses.filter(w => stronglySee(y, w))
      val (yays, nays) = ssByY.foldLeft((0, 0))((acc, cur) => {
        if (votes(cur.hash))
          (acc._1 + 1, acc._2)
        else
          (acc._1, acc._2 + 1)
      })

      val vote = yays >= nays
      val majority = math.max(yays, nays)

      if (y.round - x.round % config.c != 0) {
        // normal round
        if (majority >= superMajority()) {
          // decided
          val decided = x.decided(vote)
          (decided, votes += y.hash -> vote)
        } else {
          (x, votes += y.hash -> vote)
        }
      } else {
        // coin round
        if (majority >= superMajority()) {
          // still follow superMajority
          (x, votes += y.hash -> vote)
        } else {
          // random bit
          val randomVote = middleBit(y.hash.digest.toArray)
          (x, votes += y.hash -> randomVote)
        }
      }
    }
  }

  /**
    * @param r round
    * @return decided witness events at round r
    *
    * decide if witnesses are famous
    */
  def decideFameAt(r: Round): F[List[Event]] = {
    for {
      wits <- pool.getWitnessesAt(r).map(_.filter(!_.isDecided))
      lastRound <- pool.lastRound
      decided <- wits.traverse(x => decide(x, r + 1, lastRound)).map(_.filter(_.isDecided))
      _ <- decided.traverse(pool.putEvent)
    } yield decided
  }

  def decideFame(): F[Unit] = {
    for {
      rs <- pool.undecidedRounds
      _ <- rs.traverse(decideFameAt)
    } yield ()
  }

  def findOrderAt(r: Round): F[List[Event]] =
    for {
      events <- decideOrderAt(r)
      sorted = events.sortBy(x => (x.roundReceived, x.consensusTimestamp))
    } yield sorted

  def decideOrderAt(r: Round): F[List[Event]] = {
    def decideEvent(x: Event, round: Round, lastRound: Round): F[Event] = {
      if (x.isOrdered || round > lastRound) {
        x.pure[F]
      } else {
        for {
          fws <- pool.getFamousWitnessAt(round)
          sees = fws.filter(w => see(w, x))
          r <- if (sees.length >= fws.length / 2) {
            val timestamp = HG.median(sees.map(_.body.timestamp))
            decideEvent(x.ordered(round, timestamp), round + 1, lastRound)
          } else {
            decideEvent(x, round + 1, lastRound)
          }
        } yield r
      }
    }

    for {
      events <- pool.getEventsAt(r, !_.isOrdered)
      lastRound <- pool.lastRound
      ordered <- events.traverse(x => decideEvent(x, r + 1, lastRound))
      _ <- ordered.traverse(pool.putEvent)
    } yield ordered
  }

  def middleBit(bytes: Array[Byte]): Boolean = {
    val bv = BitVector(bytes)
    bv(bv.length / 2)
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
        val lastAncestors = self |+| sp.lastAncestors.mapValues(_ :: Nil) |+| op.lastAncestors.mapValues(_ :: Nil)
        val r = lastAncestors.mapValues(_.maxBy(_.index))
        event.updateLastAncestors(r)
      }
    }
  }

  def updateFirstDescendants(event: Event): F[Unit] = {
    def update(ancestor: Option[Event]): F[Unit] = {
      if (ancestor.isEmpty || ancestor.get.firstDescendants.contains(event.body.creator)) {
        ().pure[F]
      } else {
        val updated =
          ancestor.get.updateFirstDescendant(event.body.creator, EventCoordinates(event.hash, event.body.index))
        for {
          _ <- pool.putEvent(updated)
          sp <- pool.getEventOpt(updated.body.selfParent)
          op <- pool.getEventOpt(updated.body.otherParent)
          _ <- update(sp)
          _ <- update(op)
        } yield ()
      }
    }

    for {
      ancestors <- event.lastAncestors.toList.traverse(t => pool.getEvent(t._2.hash))
      _ <- ancestors.traverse(x => update(Some(x)))
    } yield ()
  }

  def insertEvent(event: Event): F[Unit] = {
    // TODO verify
    event.topologicalIndex = this.topologicalIndex
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

  val genesis: Event = {
    val nil = MultiHash.hash("", HashType.sha256)
    val god = MultiHash.hash("god", HashType.sha256)
    val body = EventBody(nil, nil, god, 0L, 0, Nil)
    val hash = MultiHash.hash(body, HashType.sha256)
    Event(body, hash)(topologicalIndex = 0, round = 0, isWitness = true, isFamous = Some(true))
  }
}
