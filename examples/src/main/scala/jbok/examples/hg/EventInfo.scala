package jbok.examples.hg

import jbok.crypto.hashing.MultiHash

case class EventCoordinates(hash: MultiHash, index: Int)

case class EventInfo(
    var topologicalIndex: Int = -1,
    var round: Round = -1,
    var isWitness: Boolean = false,
    var isFamous: Option[Boolean] = None,
    var roundReceived: Round = -1,
    var consensusTimestamp: Long = 0L,
    var lastAncestors: Map[MultiHash, EventCoordinates] = Map(),
    var firstDescendants: Map[MultiHash, EventCoordinates] = Map()
) {
  def isDivided = round != -1

  def isDecided = isFamous.isDefined

  def isOrdered = roundReceived != -1

  def updateLastAncestors(m: Map[MultiHash, EventCoordinates]): EventInfo = {
    this.lastAncestors = m
    this
  }

  def updateFirstDescendant(creator: MultiHash, coord: EventCoordinates): EventInfo = {
    this.firstDescendants = this.firstDescendants + (creator -> coord)
    this
  }

  def lastAncestorIndex(creator: MultiHash): Int = lastAncestors.get(creator).map(_.index).getOrElse(-1)
}
