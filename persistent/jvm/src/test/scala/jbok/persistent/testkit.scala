package jbok.persistent

import cats.effect.{IO, Resource}
import jbok.common.FileUtil
import jbok.persistent.rocksdb.RocksKVStore
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

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
}
