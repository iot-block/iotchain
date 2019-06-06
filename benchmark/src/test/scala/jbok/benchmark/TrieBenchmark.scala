package jbok.benchmark

import cats.effect.IO
import jbok.codec.HexPrefix
import jbok.codec.rlp.implicits._
import jbok.common.gen
import jbok.core.store.ColumnFamilies
import jbok.persistent.mpt.MptNode.LeafNode
import jbok.persistent.mpt.{MerklePatriciaTrie, MptNode}
import jbok.persistent.{MemoryKVStore, StageKVStore}
import org.openjdk.jmh.annotations._
import org.scalacheck.Gen

class TrieBenchmark extends JbokBenchmark {

  val store = MemoryKVStore[IO].unsafeRunSync()
  val mpt   = MerklePatriciaTrie[IO, Array[Byte], Array[Byte]](ColumnFamilies.Node, store).unsafeRunSync()
  var stage = StageKVStore(mpt)

  val size = 100000

  var i = 0

  val (keys, values) =
    (for {
      keys   <- Gen.listOfN(size, gen.boundedByteArray(0, 100))
      values <- Gen.listOfN(size, gen.boundedByteArray(0, 100))
    } yield (keys, values)).sample.get

//  @Benchmark
//  @OperationsPerInvocation(100)
//  def randomWrite() = {
//    for(_ <- 0 until 100) {
//      val key = keys(i)
//      val value = values(i)
//      mpt.putRaw(key, value).unsafeRunSync()
//      i = (i + 1) % size
//    }
//  }

  @Benchmark
  @OperationsPerInvocation(100)
  def roundtripNode() =
    for (_ <- 0 until 100) {
      val key   = keys(i)
      val value = values(i)
      val node  = LeafNode(HexPrefix.encodedToNibbles(key.encoded), value.encoded)
      val bytes = node.bytes
      bytes.decoded[MptNode]
      i = (i + 1) % size
    }

  @Benchmark
  @OperationsPerInvocation(100)
  def randomWriteState() = {
    for (_ <- 0 until 100) {
      val key   = keys(i)
      val value = values(i)
      stage = stage.put(key, value)
      i = (i + 1) % size
    }
    stage = stage.commit.unsafeRunSync()
  }
}
