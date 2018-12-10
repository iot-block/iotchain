package jbok.crypto
import java.security.SecureRandom

import cats.effect.IO
import jbok.codec.HexPrefix
import jbok.common.testkit._
import jbok.crypto.authds.mpt.MptNode.{BranchNode, ExtensionNode, LeafNode}
import jbok.crypto.authds.mpt.{MerklePatriciaTrie, MptNode}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.persistent.KeyValueDB
import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.ByteVector

object testkit {
  implicit def arbMerkleTrie: Arbitrary[MerklePatriciaTrie[IO]] = Arbitrary {
    for {
      namespace <- arbitrary[ByteVector]
      db = KeyValueDB.inmem[IO].unsafeRunSync()
    } yield MerklePatriciaTrie[IO](namespace, db).unsafeRunSync()
  }

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
