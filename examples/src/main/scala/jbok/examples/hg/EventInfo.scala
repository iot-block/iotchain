package jbok.examples.hg

import jbok.crypto.hashing.Hash

case class EventCoordinates(hash: Hash, index: Int)

case class EventInfo(
    var topologicalIndex: Int = -1,
    var round: Round = -1,
    var isWitness: Boolean = false,
    var isFamous: Option[Boolean] = None,
    var roundReceived: Round = -1,
    var consensusTimestamp: Long = 0L,
    var lastAncestors: Map[Hash, EventCoordinates] = Map(),
    var firstDescendants: Map[Hash, EventCoordinates] = Map()
) {
  def isDivided = round != -1

  def isDecided = isFamous.isDefined

  def isOrdered = roundReceived != -1

  def updateLastAncestors(m: Map[Hash, EventCoordinates]): EventInfo = {
    this.lastAncestors = m
    this
  }

  def updateFirstDescendant(creator: Hash, coord: EventCoordinates): EventInfo = {
    this.firstDescendants = this.firstDescendants + (creator -> coord)
    this
  }

  def lastAncestorIndex(creator: Hash): Int = lastAncestors.get(creator).map(_.index).getOrElse(-1)
}
