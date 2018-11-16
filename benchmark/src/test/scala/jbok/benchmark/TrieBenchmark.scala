package jbok.benchmark

import cats.effect.IO
import jbok.core.store.namespaces
import jbok.crypto.authds.mpt.{MerklePatriciaTrie, MptNode}
import jbok.persistent.{KeyValueDB, StageKeyValueDB}
import org.openjdk.jmh.annotations._
import org.scalacheck.Gen
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._
import jbok.crypto.authds.mpt.MptNode.LeafNode
import scodec.Codec
import jbok.codec.HexPrefix
import jbok.common.testkit._

class TrieBenchmark extends JbokBenchmark {

  implicit val codec: Codec[ByteVector] = rbytes.codec

  val db = KeyValueDB.inmem[IO].unsafeRunSync()
  val mpt = MerklePatriciaTrie[IO](namespaces.Node, db).unsafeRunSync()
  var stage = StageKeyValueDB[IO, ByteVector, ByteVector](namespaces.empty, mpt)

  val size = 100000

  var i = 0

  val (keys, values) =
    (for {
      keys <- Gen.listOfN(size, genBoundedByteVector(0, 100)).map(_.toArray)
      values <- Gen.listOfN(size, genBoundedByteVector(0, 100)).map(_.toArray)
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
  def roundtripNode() = {
    for(_ <- 0 until 100) {
      val key = keys(i)
      val value = values(i)
      val node = LeafNode(HexPrefix.bytesToNibbles(key), value)
      val bytes = node.bytes
      MptNode.nodeCodec.decode(bytes.bits).require.value
      i = (i + 1) % size
    }
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def randomWriteState() = {
    for(_ <- 0 until 100) {
      val key = keys(i)
      val value = values(i)
      stage = stage.put(key, value)
      i = (i + 1) % size
    }
    stage = stage.commit.unsafeRunSync()
  }
}
