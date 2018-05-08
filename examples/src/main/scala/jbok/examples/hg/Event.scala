package jbok.examples.hg

import jbok.core.Transaction
import jbok.crypto.hashing.MultiHash
import scodec.codecs._
import scodec.{Codec, _}

/**
  * @param selfParent hash of the previous self created event
  * @param otherParent hash of the received sync event
  * @param creator creator's proposition
  * @param timestamp creator's creating timestamp
  * @param txs optional payload
  *
  */
case class EventBody(
    selfParent: MultiHash,
    otherParent: MultiHash,
    creator: MultiHash,
    timestamp: Long,
    index: Int,
    txs: List[Transaction]
)

object EventBody {
  implicit val codec: Codec[EventBody] = {
    ("self parent" | Codec[MultiHash]) ::
      ("other parent" | Codec[MultiHash]) ::
      ("creator" | Codec[MultiHash]) ::
      ("timestamp" | int64) ::
      ("index" | int32) ::
      ("transactions" | listOfN(int32, Codec[Transaction]))
  }.as[EventBody]
}

case class EventCoordinates(hash: MultiHash, index: Int)

case class Event(
    body: EventBody,
    hash: MultiHash
)(
    var topologicalIndex: Int = -1,
    var round: Round = -1,
    var isWitness: Boolean = false,
    var isFamous: Option[Boolean] = None,
    var roundReceived: Round = -1,
    var consensusTimestamp: Long = 0L,
    var lastAncestors: Map[MultiHash, EventCoordinates] = Map(),
    var firstDescendants: Map[MultiHash, EventCoordinates] = Map()
) {

  override def toString: String = s"Event($hash)"

  @inline def sp = body.selfParent

  @inline def op = body.otherParent

  @inline def creator = body.creator

  @inline def isDivided = this.round != -1

  @inline def isDecided = this.isFamous.isDefined

  @inline def isOrdered = this.roundReceived != -1

  def divided(round: Round, isWitness: Boolean, isFamous: Option[Boolean] = None): Event = {
    this.round = round
    this.isWitness = isWitness
    this.isFamous = isFamous
    this
  }

  def decided(isFamous: Boolean): Event = {
    require(isDivided)
    require(!isDecided)
    this.isFamous = Some(isFamous)
    this
  }

  def ordered(roundReceived: Round, consensusTimestamp: Long): Event = {
    require(isDivided)
    require(isDecided)
    require(roundReceived > -1)
    this.roundReceived = roundReceived
    this.consensusTimestamp = consensusTimestamp
    this
  }

  def updateLastAncestors(m: Map[MultiHash, EventCoordinates]): Event = {
    this.lastAncestors = m
    this
  }

  def updateFirstDescendant(creator: MultiHash, coord: EventCoordinates): Event = {
    this.firstDescendants = this.firstDescendants + (creator -> coord)
    this
  }

  def lastAncestorIndex(creator: MultiHash): Int = lastAncestors.get(creator).map(_.index).getOrElse(-1)
}

object Event {
  implicit val ordering: Ordering[Event] =
    Ordering.by(e => (e.roundReceived, e.consensusTimestamp))
}
