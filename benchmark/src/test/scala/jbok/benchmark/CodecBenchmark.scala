package jbok.benchmark
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.models.BlockHeader
import jbok.core.testkit._
import org.openjdk.jmh.annotations._
import org.scalacheck.Gen

class CodecBenchmark extends JbokBenchmark {
  val size = 10000

  val blockHeaders = Gen.listOfN(size, arbBlockHeader.arbitrary).sample.get.toArray

  var i = 0

  @Benchmark
  def codecHeader() = {
    RlpCodec.encode(blockHeaders(i))
    i = (i + 1) % size
  }

  val codec = implicitly[RlpCodec[BlockHeader]]
  @Benchmark
  def codecHeader2() = {
    codec.encode(blockHeaders(i))
    i = (i + 1) % size
  }
}
