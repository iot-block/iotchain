package jbok.crypto.authds.merkle

import jbok.crypto._
import scodec.bits.ByteVector

case class MerkleProof(leaf: ByteVector, path: List[(ByteVector, Boolean)]) {
  def isValid(expectedRootHash: ByteVector): Boolean = {
    val rootHash = path.foldLeft(leaf) {
      case (prevHash, (hash, isLeft)) =>
        if (isLeft) {
          (prevHash ++ hash).kec256
        } else {
          (hash ++ prevHash).kec256
        }
    }

    rootHash == expectedRootHash
  }
}
