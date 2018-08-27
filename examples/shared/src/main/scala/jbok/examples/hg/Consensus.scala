package jbok.examples.hg

import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.crypto._
import jbok.persistent.KeyValueDB
import scodec.bits.{BitVector, ByteVector}

import scala.annotation.tailrec
import scala.collection.mutable
import jbok.codec.rlp.codecs._

class Consensus[F[_]](
    config: Config,
    pool: EventPool[F]
)(implicit F: Sync[F]) {
  private[this] val log = org.log4s.getLogger

  type Votes = mutable.Map[ByteVector, Boolean]

  private var topologicalIndex = 0

  val superMajority: Int = 2 * config.n / 3

  /**
    * @param x event
    * @return true if round of x should be incremented by 1
    * S is the witness events at parentRound(x)
    * ∃S ⊆ E , manyCreators(S) ∧ (∀y ∈ S, round(y) = parentRound(x) ∧ stronglySee(x, y))
    */
  def roundInc(pr: ParentRoundInfo, x: Event): F[Boolean] =
    for {
      inc <- if (pr.isRoot) {
        true.pure[F]
      } else {
        for {
          roundWits <- pool.getWitnesses(pr.round)
          ss = roundWits.map(w => Event.stronglySee(x, w, superMajority))
        } yield ss.count(_ == true) > superMajority
      }
    } yield inc

  def divideRounds(events: List[Event]): F[List[Event]] = events.traverse(divideEvent)

  def divideEvent(x: Event): F[Event] =
    for {
      sp <- pool.getEventByHash(x.sp)
      pri <- pool.getParentRound(x)
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
      round: Int,
      lastRound: Int,
      witsMap: Map[Int, List[Event]],
      votes: Votes = mutable.Map()
  ): Event = {
    @tailrec
    def decideFameByRound(
        x: Event,
        ys: List[Event],
        witnesses: List[Event],
    ): Event =
      if (x.isDecided || ys.isEmpty) {
        x
      } else {
        val decided = vote(x, ys.head, witnesses, votes)
        decideFameByRound(decided, ys.tail, witnesses)
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
  ): Event =
    if (x.isDecided) {
      x
    } else if (y.info.round - x.info.round == config.d) {
      // direct voting
      votes += y.hash -> Event.see(y, x)
      x
    } else {
      val ssByY = witnesses.filter(w => Event.stronglySee(y, w, superMajority))
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
          val randomVote = Consensus.middleBit(y.hash.toArray)
          votes += y.hash -> randomVote
          x
        }
      }
    }

  def decideFame(start: Int, end: Int): F[List[Event]] =
    for {
      witsList <- (start to end).toList.traverse(r => pool.getWitnesses(r).map(r -> _))
      witsMap = witsList.toMap
      decided = witsList.flatMap(_._2.map(w => decideWitnessFame(w, w.info.round + 1, end, witsMap)))
      _ <- decided.traverse(pool.putEvent)
    } yield decided

  def medianTimestamp(hashes: List[ByteVector]): F[Long] = {
    require(hashes.nonEmpty)
    for {
      events <- hashes.traverse(pool.getEventByHash)
    } yield Consensus.median(events.map(_.body.timestamp))
  }

  def findOrder(start: Int, end: Int): F[List[Event]] = {
    @tailrec
    def findEventOrder(x: Event, round: Int, fwsMap: Map[Int, List[Event]]): F[Event] =
      if (x.isOrdered || round > end) {
        x.pure[F]
      } else {
        val fws = fwsMap(round)
        val sees = fws.filter(w => Event.see(w, x))
        if (sees.length > fws.length / 2) {
          val hashes = sees.flatMap(w => Event.earliestSelfAncestorSee(w, x))
          for {
            ts <- medianTimestamp(hashes)
          } yield x.ordered(round, ts)
        } else {
          findEventOrder(x, round + 1, fwsMap)
        }
      }

    for {
      fws <- (start + 1 to end).toList.traverse(i => pool.getFamousWitnesses(i).map(i -> _))
      fwsMap = fws.toMap
      unordered <- (start to end).toList.flatTraverse(r => pool.getEvents(r))
      ordered <- unordered.traverse(x => findEventOrder(x, x.round + 1, fwsMap))
      _ <- ordered.traverse(pool.putEvent)
    } yield ordered
  }

  def insertEvent(event: Event): F[Unit] = {
    event.info.topologicalIndex = this.topologicalIndex
    this.topologicalIndex += 1
    for {
      updated <- pool.updateLastAncestors(event)
      _ = log.info(s"update last ancestors")
      _ <- pool.putEvent(updated)
      _ = log.info(s"put event")
      _ <- pool.updateFirstDescendants(updated)
      _ = log.info(s"update first descendants")
    } yield ()
  }
}

object Consensus {
  def apply[F[_]: Sync](config: Config, pool: EventPool[F]): Consensus[F] =
    new Consensus[F](config, pool)

  def median[A: Ordering](xs: Seq[A]): A = {
    require(xs.nonEmpty)
    xs.sorted(implicitly[Ordering[A]])(xs.length / 2)
  }

  def middleBit(bytes: Array[Byte]): Boolean = {
    val bv = BitVector(bytes)
    bv(bv.length / 2)
  }

  val genesis: Event = {
    val nil = "".utf8bytes.kec256
    val god = "god".utf8bytes.kec256
    val body = EventBody(nil, nil, god, 0L, 0, Nil)
    val hash = RlpCodec.encode(body).require.bytes.kec256
    val event = Event(body, hash)
    event.info.topologicalIndex = 0
    event.info.round = 0
    event.info.isWitness = true
    event.info.isFamous = Some(true)
    event.info.roundReceived = 1
    event
  }
}
