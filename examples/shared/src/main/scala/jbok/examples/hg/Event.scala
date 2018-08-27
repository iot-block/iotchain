package jbok.examples.hg

import scodec.bits.ByteVector

case class Event(
    body: EventBody,
    hash: ByteVector
) {
  val info: EventInfo = EventInfo()

  override def toString: String = s"Event($hash)"

  @inline def sp = body.selfParent

  @inline def op = body.otherParent

  @inline def creator = body.creator

  @inline def round = info.round

  @inline def isWitness = info.isWitness

  @inline def isFamous = info.isFamous

  @inline def isDivided = info.round != -1

  @inline def isDecided = info.isFamous.isDefined

  @inline def isOrdered = info.roundReceived != -1

  def divided(round: Int, isWitness: Boolean, isFamous: Option[Boolean] = None): Event = {
    info.round = round
    info.isWitness = isWitness
    info.isFamous = isFamous
    this
  }

  def decided(isFamous: Boolean): Event = {
    require(isDivided)
    require(!isDecided)
    info.isFamous = Some(isFamous)
    this
  }

  def ordered(roundReceived: Int, consensusTimestamp: Long): Event = {
    require(isDivided)
    require(isDecided)
    require(roundReceived > -1)
    info.roundReceived = roundReceived
    info.consensusTimestamp = consensusTimestamp
    this
  }

  def updateLastAncestors(m: Map[ByteVector, EventCoordinates]): Event = {
    info.lastAncestors = m
    this
  }

  def updateFirstDescendant(creator: ByteVector, coord: EventCoordinates): Event = {
    info.firstDescendants += (creator -> coord)
    this
  }
}

object Event {
  implicit val ordering: Ordering[Event] = Ordering.by(e => (e.info.roundReceived, e.info.consensusTimestamp))

  /**
    * @param x event
    * @param y event
    * @return true if y is an ancestor of x
    *
    * x == y ∨ ∃z ∈ parents(x), ancestor(z, y)
    */
  def ancestor(x: Event, y: Event): Boolean =
    x == y || x.info.lastAncestorIndex(y.body.creator) >= y.body.index

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
    * @return earliest selfAncestor of x that sees y
    */
  def earliestSelfAncestorSee(x: Event, y: Event): Option[ByteVector] = {
    val a = y.info.firstDescendants(x.creator)

    if (x.body.index >= a.index) {
      Some(a.hash)
    } else {
      None
    }
  }

  /**
    * @param x event
    * @param y event
    * @return true if x strongly sees y
    *
    * see(x, y) ∧ (∃S ⊆ E, manyCreators(S) ∧(z∈S => (see(x,z)∧see(z,y))))
    */
  def stronglySee(x: Event, y: Event, superMajority: Int): Boolean = {
    val c = x.info.lastAncestors
      .map {
        case (creator, coord) =>
          coord.index >= y.info.firstDescendants.get(creator).map(_.index).getOrElse(Int.MaxValue)
      }
      .count(_ == true)
    c > superMajority
  }
}
