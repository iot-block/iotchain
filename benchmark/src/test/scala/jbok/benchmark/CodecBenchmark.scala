package jbok.benchmark

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.StatelessGen
import jbok.core.models.BlockHeader
import org.openjdk.jmh.annotations._
import org.scalacheck.Gen

class CodecBenchmark extends JbokBenchmark {
  val size = 10000

  val xs = Gen.listOfN(size, StatelessGen.blockHeader).sample.get.toArray

  var i = 0

  @Benchmark
  @OperationsPerInvocation(1000)
  def derive_1k() =
    (0 until 1000).foreach { _ =>
      xs(i).encoded
      i = (i + 1) % size
    }

  val codec = RlpCodec[BlockHeader]
  @Benchmark
  @OperationsPerInvocation(1000)
  def derive_cached_1k() =
    (0 until 1000).foreach { _ =>
      xs(i).encoded(codec)
      i = (i + 1) % size
    }
}
