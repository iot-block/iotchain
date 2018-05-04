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
  */
case class EventBody(
    selfParent: MultiHash,
    otherParent: MultiHash,
    creator: MultiHash,
    timestamp: Long,
    index: Int,
    txs: List[Transaction]
) {
  lazy val parents: List[MultiHash] = List(selfParent, otherParent)
}

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
    hash: MultiHash,
    round: Round = -1,
    isWitness: Boolean = false,
    isFamous: Option[Boolean] = None,
    roundReceived: Round = -1,
    consensusTimestamp: Long = 0L,
    lastAncestors: Map[MultiHash, EventCoordinates],
    firstDescendants: Map[MultiHash, EventCoordinates]
) {
  def isDivided = this.round != -1

  def isDecided = this.isFamous.isDefined

  def divided(round: Round, isWitness: Boolean): Event = {
    this.copy(round = round, isWitness = isWitness)
  }

  def decided(isFamous: Boolean) = {
    require(isDivided)
    require(!isDecided)
    this.copy(isFamous = Some(isFamous))
  }

  def consensused(roundReceived: Round, consensusTimestamp: Long) = {
    require(isDivided)
    require(isDecided)
    this.copy(roundReceived = roundReceived, consensusTimestamp = consensusTimestamp)
  }
}
