package jbok.benchmark
import org.openjdk.jmh.annotations.Benchmark
import jbok.crypto._

class HashBenchmark extends JbokBenchmark {
  val s = "hash benchmark"
  val b = s.utf8bytes

  @Benchmark
  def kec256() = {
    b.kec256
  }

  @Benchmark
  def ripemd160() = {
    b.ripemd160
  }

  @Benchmark
  def kec512() = {
    b.kec512
  }

  @Benchmark
  def sha256() = {
    b.sha256
  }
}
