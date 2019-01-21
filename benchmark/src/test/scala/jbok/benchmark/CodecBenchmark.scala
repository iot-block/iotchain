package jbok.benchmark

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.models.BlockHeader
import jbok.core.testkit._
import org.openjdk.jmh.annotations._
import org.scalacheck.Gen

class CodecBenchmark extends JbokBenchmark {
  val size = 10000

  val xs = Gen.listOfN(size, arbBlockHeader.arbitrary).sample.get.toArray

  var i = 0

  @Benchmark
  @OperationsPerInvocation(1000)
  def derive_1k() =
    (0 until 1000).foreach { _ =>
      xs(i).asValidBytes
      i = (i + 1) % size
    }

  val codec = RlpCodec[BlockHeader]
  @Benchmark
  @OperationsPerInvocation(1000)
  def derive_cached_1k() =
    (0 until 1000).foreach { _ =>
      xs(i).asValidBytes(codec)
      i = (i + 1) % size
    }
}
