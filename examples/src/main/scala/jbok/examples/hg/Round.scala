package jbok.examples.hg

import jbok.crypto.hashing.MultiHash

case class ParentRoundInfo(round: Round, isRoot: Boolean)

case class Root(sp: MultiHash, op: MultiHash, index: Int = -1, round: Round = -1, others: Map[String, String] = Map())

case class RoundInfo(
    hash: MultiHash,
    round: Round,
    isWitness: Boolean,
    isFamous: Option[Boolean] = None
)
