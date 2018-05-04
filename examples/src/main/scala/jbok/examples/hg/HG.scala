package jbok.examples.hg

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import jbok.common._
import jbok.crypto.hashing.MultiHash
import scodec.bits.BitVector

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
    * @param x event hash
    * @param y event hash
    * @return true if y is an selfAncestor of x
    *
    * x and y are created by a same creator
    * x == y ∨ (selfParent(x) ̸= ∅ ∧ selfAncestor(selfParent(x), y))
    */
  def selfAncestor(x: E, y: E): F[Boolean] =
    (x == y).pure[F] || (for {
      ex <- pool.getEvent(x)
      ey <- pool.getEvent(y)
    } yield ex.body.creator == ey.body.creator && ex.body.index >= ey.body.index)

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
    * @return the max round of {x.selfParent, x.otherParent}
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
    * @param x event hash
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
      root <- pool.getRoot(ex.hash)
    } yield ex.body.selfParent == root.sp && ex.body.otherParent == root.op

  /**
    *
    * @param x event
    * @return true if x is a witness (first event of a round for the owner)
    *
    * (selfParent(x) = ∅) ∨ (round(x) > round(selfParent(x))
    */
  def witness(x: Event): F[Boolean] = {
    require(!x.isDivided)

    isFirstEvent(x) || (for {
      r <- round(x)
      spr <- round(x)
    } yield r > spr)
  }

  /**
    * @param x event
    * @param y event
    * @return round(x) − round(y)
    */
  def diff(x: Event, y: Event): Round = {
    require(x.isDivided)
    require(y.isDivided)
    x.round - y.round
  }

  /**
    *
    * @param x event
    * @param y event
    * @return
    *
    * |{z ∈ Z | diff(x, z) = 1 ∧ witness(z) ∧ stronglySee(x, z) ∧ vote(z, y) = v}|
    *
    * if x can strongly sees z, and z have voted y
    * then x vote y (copy from z vote y)
    */
  def votes(x: Event, y: Event): F[(Int, Int)] =
    for {
      witnesses <- pool.getWitnessesAt(x.round - 1)
      ssWitnesses = witnesses.filter(z => stronglySee(x, z))
      counts <- ssWitnesses.traverse(z => vote(z, y))
    } yield {
      val yays = counts.count(_ == true)
      val nays = counts.count(_ == false)
      (yays, nays)
    }

  /**
    *
    * @param x witness event
    * @param y witness event
    * @return yays / (yays + nays)
    *
    */
  def fractTrue(x: Event, y: Event): F[Double] =
    for {
      vs <- votes(x, y)
    } yield {
      val (yays, nays) = vs
      yays.toDouble / (yays + nays)
    }

  /**
    *
    * @param x witness event
    * @param y witness event
    * @return (selfParent(x) ̸= ∅ ∧ decide(selfParent(x), y)
    */
  def copyVote(x: Event, y: Event): F[Boolean] = {
    require(x.isWitness)
    require(y.isWitness)
    !isFirstEvent(x) && decide(x, y)
  }

  /**
    * @param x witness event
    * @param y witness event
    * @return true if x votes y
    *
    * - vote(selfParent(x), y) => copyVote(x)
    * - vote(x, y) => see(x, y) if diff(x, y) == d
    * - vote(x, y) => middleBit(x) if diff(x, y) != d && diff(x, y) % c == 0
    * - vote(x, y) => fractTrue(x, y) >= 0.5
    */
  def vote(x: Event, y: Event): F[Boolean] = {
    require(x.isWitness)
    require(y.isWitness)

    val d = diff(x, y)

    if (d == config.d) {
      see(x, y).pure[F] // direct voting
    } else {
      copyVote(x, y).flatMap(cp =>
        if (cp) {
          pool.getEvent(x.body.selfParent).flatMap(spe => vote(spe, y)) // copy vote from selfParent
        } else {
          for {
            ft <- fractTrue(x, y)
          } yield {
            if (ft >= 1 / 3.0 && ft <= 2 / 3.0 && d % config.c == 0) {
              middleBit(x.body.creator.digest.toArray) // coin round
            } else {
              ft >= 0.5 // super majority
            }
          }
      })
    }
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
      divided <- events.traverse(divideRound)
      _ <- divided.traverse(e => pool.putRoundInfo(e))
    } yield divided
  }

  /**
    * @param event Event
    * @return
    *
    * divide round and witness for an event
    */
  def divideRound(event: Event): F[Event] = {
    require(!event.isDivided)
    for {
      r <- round(event)
      wit <- witness(event)
    } yield event.divided(r, wit)
  }

  /**
    * @param x event
    * @param y event
    * @return true if x's famous if decided by y
    *
    *  (selfParent(x) ̸= ∅ ∧ decide(selfParent(x), y)) ∨(witness(x) ∧ witness(y)
    *  ∧diff(x,y)>d∧(diff(x,y)modc>0)∧(∃v∈B,votes(x,y,v)> 2n)))
    */
  def decide(x: Event, y: Event): F[Boolean] = {
    val cond2 = (diff(x, y) > config.d && diff(x, y) % config.c != 0).pure[F] &&
      votes(x, y).map {
        case (yays, nays) =>
          yays > superMajority() || nays > superMajority()
      }

    isFirstEvent(x).flatMap {
      case true => cond2
      case false => pool.getEvent(x.body.selfParent).flatMap(spe => decide(spe, y))
    }
  }

  def decideWitnessFame(x: Event): F[Event] = {
    require(x.isDivided)

    for {
      lastRound <- pool.lastRound
      wits <- (x.round + 1 to lastRound).toList.flatTraverse(r => pool.getWitnessesAt(r))
      decided <- wits.traverse(y =>
        decide(y, x).flatMap {
          case true => vote(y, x).map(_.some)
          case false => none[Boolean].pure[F]
      })
    } yield {
      x.decided(decided.find(_.isDefined).flatten.getOrElse(false))
    }
  }

  /**
    * @param r round
    * @return true if round r is fully decided
    *
    * decide if witnesses are famous
    */
  def decideFameAt(r: Round): F[Unit] =
    for {
      wits <- pool.getWitnessesAt(r)
      updated <- wits.traverse(decideWitnessFame)
      _ <- updated.traverse(pool.putEvent)
    } yield ()

  def decideFame(): F[Unit] =
    for {
      rounds <- pool.undecidedRounds
      _ <- rounds.traverse(decideFameAt)
    } yield ()

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
      consensused = events.zip(received).map { case (e, r) => e.consensused(r, 0L) }
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
        case Valid(e) => ???
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
