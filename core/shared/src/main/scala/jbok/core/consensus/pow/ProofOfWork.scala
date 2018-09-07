package jbok.core.consensus.pow

import scodec.bits.ByteVector

case class ProofOfWork(mixHash: ByteVector, difficultyBoundary: ByteVector)
