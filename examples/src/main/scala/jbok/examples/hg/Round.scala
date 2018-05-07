package jbok.examples.hg

import jbok.crypto.hashing.MultiHash

case class ParentRoundInfo(round: Round, isRoot: Boolean)

case class EventInfo(
    hash: MultiHash,
    round: Round,
    isWitness: Boolean,
    isFamous: Option[Boolean],
    isOrdered: Boolean
)
