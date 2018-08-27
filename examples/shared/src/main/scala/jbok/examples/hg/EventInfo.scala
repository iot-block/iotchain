package jbok.examples.hg

import scodec.bits.ByteVector

case class EventCoordinates(hash: ByteVector, index: Int)

case class EventInfo(
    var topologicalIndex: Int = -1,
    var round: Int = -1,
    var isWitness: Boolean = false,
    var isFamous: Option[Boolean] = None,
    var roundReceived: Int = -1,
    var consensusTimestamp: Long = 0L,
    var lastAncestors: Map[ByteVector, EventCoordinates] = Map(),
    var firstDescendants: Map[ByteVector, EventCoordinates] = Map()
) {
  def isDivided = round != -1

  def isDecided = isFamous.isDefined

  def isOrdered = roundReceived != -1

  def updateLastAncestors(m: Map[ByteVector, EventCoordinates]): EventInfo = {
    this.lastAncestors = m
    this
  }

  def updateFirstDescendant(creator: ByteVector, coord: EventCoordinates): EventInfo = {
    this.firstDescendants = this.firstDescendants + (creator -> coord)
    this
  }

  def lastAncestorIndex(creator: ByteVector): Int = lastAncestors.get(creator).map(_.index).getOrElse(-1)
}
