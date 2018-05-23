package jbok.crypto.authds.merkle

import jbok.crypto._
import jbok.crypto.hashing._
import scodec.bits.ByteVector

case class MerkleProof[H: Hashing](leaf: ByteVector, path: List[(ByteVector, Boolean)]) {
  def isValid(expectedRootHash: ByteVector): Boolean = {
    val rootHash = path.foldLeft(leaf) {
      case (prevHash, (hash, isLeft)) =>
        if (isLeft) {
          (prevHash ++ hash).digested
        } else {
          (hash ++ prevHash).digested
        }
    }

    rootHash == expectedRootHash
  }
}
