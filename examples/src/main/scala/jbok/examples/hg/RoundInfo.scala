package jbok.examples.hg

import jbok.crypto.hashing.Hash

import scala.collection.mutable

case class ParentRoundInfo(round: Round, isRoot: Boolean)

case class RoundInfo(round: Round, events: mutable.Map[Hash, EventInfo] = mutable.Map()) {
  def isDecided = events.forall { case (_, ei) => !ei.isWitness || ei.isWitness && ei.isFamous.isDefined }

  def isOrdered = events.forall { case (_, ei) => ei.isOrdered }

  def +=(event: Event): RoundInfo = {
    this.events += (event.hash -> event.info)
    this
  }

  def -=(hash: Hash): RoundInfo = {
    this.events -= hash
    this
  }
}
