package jbok.benchmark
import jbok.ModelGen
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import org.openjdk.jmh.annotations._
import org.scalacheck.Gen

class CodecBenchmark extends JbokBenchmark {
  val size = 10000

  val blockHeaders = Gen.listOfN(size, ModelGen.blockHeaderGen).sample.get.toArray

  var i = 0

  @Benchmark
  def codecHeader() = {
    RlpCodec.encode(blockHeaders(i))
    i = (i + 1) % size
  }
}
