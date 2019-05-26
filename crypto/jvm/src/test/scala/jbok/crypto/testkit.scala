package jbok.crypto
import java.security.SecureRandom

import cats.effect.IO
import jbok.codec.HexPrefix
import jbok.common.testkit._
import jbok.crypto.authds.mpt.MptNode
import jbok.crypto.authds.mpt.MptNode.{BranchNode, ExtensionNode, LeafNode}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import org.scalacheck.{Arbitrary, Gen}

object testkit {

  implicit val arbBranchNode: Arbitrary[BranchNode] = Arbitrary {
    for {
      children <- Gen
        .listOfN(16, genBoundedByteVector(32, 32))
        .map(childrenList => childrenList.map(child => Some(Left(child))))
      value <- Gen.option(arbByteVector.arbitrary)
    } yield BranchNode(children, value)
  }

  implicit val arbExtensionNode: Arbitrary[ExtensionNode] = Arbitrary {
    for {
      key   <- genBoundedByteVector(32, 32)
      value <- genBoundedByteVector(32, 32)
    } yield ExtensionNode(HexPrefix.bytesToNibbles(key), Left(value))
  }

  implicit val arbLeafNode: Arbitrary[LeafNode] = Arbitrary {
    for {
      key   <- genBoundedByteVector(32, 32)
      value <- genBoundedByteVector(32, 32)
    } yield LeafNode(HexPrefix.bytesToNibbles(key), value)
  }

  implicit val arbMptNode: Arbitrary[MptNode] = Arbitrary {
    Gen.oneOf[MptNode](arbLeafNode.arbitrary, arbExtensionNode.arbitrary, arbBranchNode.arbitrary)
  }

  def genKeyPair: Gen[KeyPair] = {
    Signature[ECDSA].generateKeyPair[IO](Some(new SecureRandom())).unsafeRunSync()
  }

  implicit def arbKeyPair: Arbitrary[KeyPair] = Arbitrary {
    genKeyPair
  }
}
