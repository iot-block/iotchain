package jbok.examples.hg

import jbok.crypto.hashing.MultiHash

import scala.collection.mutable

case class ParentRoundInfo(round: Round, isRoot: Boolean)

case class RoundInfo(round: Round, events: mutable.Map[MultiHash, EventInfo] = mutable.Map()) {
  def isDecided = events.forall { case (_, ei) => !ei.isWitness || ei.isWitness && ei.isFamous.isDefined }

  def isOrdered = events.forall { case (_, ei) => ei.isOrdered }

  def update(event: Event): RoundInfo = {
    this.events += (event.hash -> EventInfo(
        event.hash,
        event.round,
        event.isWitness,
        event.isFamous,
        event.isOrdered))
    this
  }
}

case class EventInfo(
    hash: MultiHash,
    round: Round,
    isWitness: Boolean,
    isFamous: Option[Boolean],
    isOrdered: Boolean
)
