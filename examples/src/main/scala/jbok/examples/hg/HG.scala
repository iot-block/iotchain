package jbok.examples.hg

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import jbok.common._
import jbok.crypto.hashing.MultiHash
import scodec.bits.BitVector

import scala.annotation.tailrec
import scala.collection.mutable

case class HGConfig(
    n: Int, // the number of members in the population
    c: Int = 10, // frequency of coin rounds (such as c = 10)
    d: Int = 1 // rounds delayed before start of election (such as d = 1)
)

abstract class HG[F[_]: Monad, P](
    pool: Pool[F],
    config: HGConfig
) {
  type E = MultiHash

  def superMajority(n: Int = config.n): Int = 2 * n / 3

  /**
    * @param x event
    * @param y event
    * @return true if y is an ancestor of x
    *
    * x == y ∨ ∃z ∈ parents(x), ancestor(z, y)
    */
  def ancestor(x: Event, y: Event): Boolean =
    x == y || x.lastAncestors(y.body.creator).index >= y.body.index

  /**
    * @param x event
    * @param y event
    * @return true if y is a selfAncestor of x
    *
    * x and y are created by a same creator
    * x == y ∨ (selfParent(x) ̸= ∅ ∧ selfAncestor(selfParent(x), y))
    */
  def selfAncestor(x: Event, y: Event): Boolean =
    x == y || x.body.creator == y.body.creator && x.body.index >= y.body.index

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
    * @return true if ex strongly sees ey
    *
    * see(x, y) ∧ (∃S ⊆ E, manyCreators(S) ∧(z∈S => (see(x,z)∧see(z,y))))
    */
  def stronglySee(x: Event, y: Event): Boolean = {
    val c = x.lastAncestors
      .map { case (h, coord) => coord.index >= y.firstDescendants(h).index }
      .count(_ == true)
    c >= superMajority(config.n)
  }

  /**
    * @param x event
    * @return the max of {selfParentRound, otherParentRound}
    */
  def parentRound(x: Event): F[ParentRoundInfo] =
    for {
      root <- pool.getRoot(x.body.creator)
      spr <- selfParentRound(x, root)
      opr <- otherParentRound(x, root)
    } yield {
      if (spr.round >= opr.round) spr else opr
    }

  def selfParentRound(e: Event, root: Root): F[ParentRoundInfo] = {
    val sp = e.body.selfParent
    if (sp == root.sp) {
      // If it is the creator's first Event, use the corresponding Root
      ParentRoundInfo(root.round, isRoot = true).pure[F]
    } else {
      // get the thr round of the self parent
      for {
        spe <- pool.getEvent(sp)
        spr <- round(spe)
      } yield {
        ParentRoundInfo(spr, isRoot = false)
      }
    }
  }

  def otherParentRound(e: Event, root: Root): F[ParentRoundInfo] = {
    val op = e.body.otherParent
    for {
      ope <- pool.getEventOpt(op)
    } yield {
      if (ope.isDefined) {
        //if we known the other-parent, fetch its Round directly
        ParentRoundInfo(ope.get.round, isRoot = false)
      } else if (op == root.op) {
        //we do not know the other-parent but it is referenced in Root.Y
        ParentRoundInfo(root.round, isRoot = true)
      } else {
        //we do not know the other-parent but it is referenced in root.others
        //we use the root's round
        //in reality the OtherParent Round is not necessarily the same as the
        //Root's but it is necessarily smaller. Since We are interest in the
        //max between self-parent and other-parent rounds, this shortcut is
        //acceptable.
        ParentRoundInfo(root.round, isRoot = false)
      }
    }
  }

  /**
    * @param x event
    * @return true if round of x should be incremented by 1
    * S is the witness events at parentRound(x)
    * ∃S ⊆ E , manyCreators(S) ∧ (∀y ∈ S, round(y) = parentRound(x) ∧ stronglySee(x, y))
    */
  def roundInc(x: Event): F[Boolean] =
    for {
      pr <- parentRound(x)
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
        } yield ss.count(_ == true) >= superMajority(config.n)
      }
    } yield inc

  /**
    * @param x event
    * @return the round of x
    *
    * parentRound(x) + (1 if roundInc(x) 0 otherwise)
    */
  def round(x: Event): F[Round] = {
    require(!x.isDivided)

    for {
      pr <- parentRound(x).map(_.round)
      inc <- roundInc(x)
    } yield {
      if (inc) {
        pr + 1
      } else {
        pr
      }
    }
  }

  def isFirstEvent(ex: Event): F[Boolean] =
    for {
      root <- pool.getRoot(ex.body.creator)
    } yield ex.body.selfParent == root.sp && ex.body.otherParent == root.op

  /**
    *
    * @param x event
    * @return true if x is a witness (first event of a round for the owner)
    *
    * (selfParent(x) = ∅) ∨ (round(x) > round(selfParent(x))
    */
  def witness(x: Event): F[Boolean] =
    isFirstEvent(x) || pool.getEvent(x.body.selfParent).map(sp => x.round > sp.round)

  /**
    * @param x event
    * @param y event
    * @return round(x) − round(y)
    */
  def diff(x: Event, y: Event): Round = {
    require(x.isDivided)
    require(y.isDivided)
    val d = x.round - y.round
    require(d >= 0)
    d
  }

  /**
    * The divideRounds procedure.
    * As soon as an event x is known, it is assigned a round number x.round,
    * and the boolean value x.witness is calculated, indicating whether it is a “witness”,
    * the first event that a member created in that round.
    */
  def divideRounds(events: List[Event]): F[List[Event]] = {
    require(events.forall(!_.isDivided))
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
      r <- round(event)
      wit <- witness(event.copy(round = r))
    } yield event.divided(r, wit)
  }

  def decide(
      x: Event,
      round: Round,
      lastRound: Round,
      votes: mutable.Map[MultiHash, Boolean] = mutable.Map()): F[Event] =
    if (x.isDecided || round > lastRound) {
      x.pure[F]
    } else {
      for {
        ys <- pool.getWitnessesAt(round)
        witnesses <- pool.getWitnessesAt(round - 1)
        (decided, votes) = decide(x, ys, witnesses, votes)
        n <- decide(decided, round + 1, lastRound, votes)
      } yield n
    }

  @tailrec
  final def decide(x: Event, ys: List[Event], witnesses: List[Event], votes: mutable.Map[MultiHash, Boolean]): Event =
    if (x.isDecided || ys.isEmpty) {
      x
    } else {
      val (decided, votes) = decide(x, ys.head, witnesses, votes)
      if (decided.isDecided)
        decided
      else
        decide(x, ys.tail, witnesses, votes)
    }

  /**
    *
    * @param x event
    * @param y event
    * @param witnesses witness events at round (y.round - 1)
    * @param votes all votes for x
    * @return
    */
  def decide(
      x: Event,
      y: Event,
      witnesses: List[Event],
      votes: mutable.Map[MultiHash, Boolean]): (Event, mutable.Map[MultiHash, Boolean]) =
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
          // still folow superMajority
          (x, votes += y.hash -> vote)
        } else {
          // random bit
          val randomVote = middleBit(y.hash.digest.toArray)
          (x, votes += y.hash -> randomVote)
        }
      }
    }

  /**
    * @param r round
    * @return decided witness events at round r
    *
    * decide if witnesses are famous
    */
  def decideFameAt(r: Round): F[List[Event]] =
    for {
      wits <- pool.getWitnessesAt(r).map(_.filter(!_.isDecided))
      lastRound <- pool.lastRound
      decided <- wits.traverse(x => decide(x, r + 1, lastRound)).map(_.filter(_.isDecided))
    } yield decided

  /**
    * once all the witnesses in round r have their fame decided
    * find the set of famous witnesses in that round
    * then remove from that set any famous witness that has the
    * same creator as any other in that set
    *
    * the remaining famous witnesses are the *unique famous witnesses*
    * they act as the judges to assign earlier events a *round received* and *consensus timestamp*
    *
    * once these have been calculated,
    * the events are sorted by round received.
    * any ties are subsorted by consensus timestamp.
    * any remaining ties are subsorted by whitened signature.
    *
    * The whitened signature is the signature XORed with
    * the signatures of all unique famous witnesses in the received round
    */
  def findOrderAt(r: Round): F[Unit] =
    for {
      _ <- roundReceived(r)
      events <- pool.undividedEventsAt(r)
      sorted = events.sortBy(x => (x.roundReceived, x.consensusTimestamp))
      _ <- handleNewConsensusEvents(sorted)
    } yield ()

  def findOrder(): F[Unit] =
    for {
      rounds <- pool.undecidedRounds
      _ <- rounds.traverse(findOrderAt)
    } yield ()

  /**
    * assign round received and timestamp to events
    */
  def roundReceived(r: Round): F[Unit] = {
    def receive(x: Event, round: Round, roundReceived: Option[Round] = None): F[Round] =
      roundReceived match {
        case Some(r) => r.pure[F]
        case None =>
          for {
            fws <- pool.getFamousWitnessAt(round + 1)
            sees = fws.map(w => see(w, x))
            rc = if (sees.count(_ == true) > sees.length / 2) Some(round + 1) else None
            nr <- receive(x, round + 1, rc)
          } yield nr
      }

    for {
      events <- pool.undividedEventsAt(r)
      received <- events.traverse(x => receive(x, r + 1))
      consensused = events.zip(received).map { case (e, r) => e.consensused(r, 0L) } // TODO
      _ <- consensused.traverse(pool.putEvent)
    } yield ()
  }

  def middleBit(bytes: Array[Byte]): Boolean = {
    val bv = BitVector(bytes)
    bv(bv.length / 2)
  }

  def runConsensus(): F[Unit] =
    for {
      undivided <- pool.undividedEvents
      divided <- divideRounds(undivided)
      _ <- divided.traverse(pool.putEvent)
      rounds <- pool.undecidedRounds
      _ <- rounds.traverse(r => decideFameAt(r).flatMap(_ => findOrderAt(r)))
    } yield ()

  def consensusAt(r: Round): F[Unit] =
    for {
      undivided <- pool.undividedEvents
      divided <- divideRounds(undivided)
      _ <- divided.traverse(pool.putEvent)
      _ <- decideFameAt(r)
      _ <- findOrderAt(r)
    } yield ()

  def handleNewConsensusEvents(events: List[Event]): F[Unit] = ???

  def insetEvent(event: Event): F[ValidatedNel[HGError, Unit]] =
    for {
      verified <- verifyEvent(event)
    } yield {
      verified match {
        case Valid(_) => ???
        case Invalid(nel) => ???
      }
    }

  def verifyEvent(event: Event): F[ValidatedNel[HGError, Unit]] =
    for {
      verifySp <- verifySelfParent(event)
    } yield verifySp

  def verifySelfParent(event: Event): F[ValidatedNel[HGError, Unit]] = {
    val sp = event.body.selfParent
    val creator = event.body.creator
    for {
      creatorLastKnown <- pool.lastEventFrom(creator)
    } yield {
      val legit = creatorLastKnown.contains(sp)
      if (legit)
        ().validNel
      else SelfParentError.invalidNel
    }
  }

  def verifyOtherParent(event: Event): F[ValidatedNel[HGError, Unit]] =
    ().validNel[HGError].pure[F]

}

sealed trait HGError
case object SelfParentError extends HGError
