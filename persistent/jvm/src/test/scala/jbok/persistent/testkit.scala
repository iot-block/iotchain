package jbok.persistent

import cats.effect.{IO, Resource}
import jbok.codec.HexPrefix
import jbok.common.{gen, FileUtil}
import jbok.persistent.rocksdb.RocksKVStore
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._
import jbok.persistent.mpt.MptNode
import jbok.persistent.mpt.MptNode._
import cats.implicits._

object testkit {
  implicit def arbColumnFamily: Arbitrary[ColumnFamily] = Arbitrary {
    Gen.alphaNumStr.map(ColumnFamily.apply)
  }

  def testRocksKVStore(cfs: List[ColumnFamily] = List(ColumnFamily.default)): Resource[IO, KVStore[IO]] =
    FileUtil[IO].temporaryDir().flatMap { dir =>
      RocksKVStore.resource[IO](dir.path, cfs)
    }

  val testMemoryKVStore: Resource[IO, KVStore[IO]] =
    Resource.liftF(MemoryKVStore[IO])

  def testRocksStageStore(cfs: List[ColumnFamily] = List(ColumnFamily.default)): Resource[IO, StageKVStore[IO, ByteVector, ByteVector]] =
    testRocksKVStore(cfs).map(inner => StageKVStore(SingleColumnKVStore[IO, ByteVector, ByteVector](ColumnFamily.default, inner)))

  val testMemoryStageStore: Resource[IO, StageKVStore[IO, ByteVector, ByteVector]] =
    testMemoryKVStore.map(inner => StageKVStore(SingleColumnKVStore[IO, ByteVector, ByteVector](ColumnFamily.default, inner)))

  implicit lazy val arbLeafNode: Arbitrary[LeafNode] = Arbitrary {
    for {
      key   <- gen.boundedByteVector(0, 1024)
      value <- gen.boundedByteVector(0, 1024)
    } yield LeafNode(HexPrefix.encodedToNibbles(key.encoded), value.encoded)
  }

  implicit lazy val arbBranchNode: Arbitrary[BranchNode] = Arbitrary {
    for {
      children <- Gen
        .listOfN(16, Gen.oneOf(gen.sizedByteVector(32).map(_.asLeft), arbMptNode.arbitrary.map(_.asRight)))
        .map(childrenList => childrenList.map(child => Some(child)))
      value <- gen.byteVector
    } yield BranchNode(children, Some(value.encoded))
  }

  implicit lazy val arbExtensionNode: Arbitrary[ExtensionNode] = Arbitrary {
    for {
      key   <- gen.boundedByteVector(0, 1024)
      value <- gen.boundedByteVector(0, 1024)
    } yield ExtensionNode(HexPrefix.encodedToNibbles(key.encoded), Left(value))
  }

  implicit lazy val arbMptNode: Arbitrary[MptNode] = Arbitrary {
    Gen.oneOf[MptNode](arbLeafNode.arbitrary, arbExtensionNode.arbitrary, arbBranchNode.arbitrary)
  }
}
