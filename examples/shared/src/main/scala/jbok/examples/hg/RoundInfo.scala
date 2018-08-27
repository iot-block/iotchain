package jbok.examples.hg

import scodec.bits.ByteVector

case class ParentRoundInfo(round: Int, isRoot: Boolean)

case class RoundInfo(round: Int, events: Map[ByteVector, EventInfo] = Map()) {
  def isDecided = events.forall { case (_, ei) => !ei.isWitness || ei.isWitness && ei.isFamous.isDefined }

  def isOrdered = events.forall { case (_, ei) => ei.isOrdered }

  def +=(event: Event): RoundInfo =
    copy(events = this.events + (event.hash -> event.info))

  def -=(hash: ByteVector): RoundInfo =
    copy(events = this.events - hash)
}
