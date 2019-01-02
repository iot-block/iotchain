package jbok.core.consensus.pow

import scodec.bits.ByteVector

final case class ProofOfWork(mixHash: ByteVector, difficultyBoundary: ByteVector)
