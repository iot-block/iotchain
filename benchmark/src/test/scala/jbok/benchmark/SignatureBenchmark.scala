package jbok.benchmark
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, Signature}
import org.openjdk.jmh.annotations.Benchmark

class SignatureBenchmark extends JbokBenchmark {
  val s = "hash benchmark"
  val b = s.utf8bytes
  val h = b.kec256.toArray
  val k = Signature[ECDSA].generateKeyPair().unsafeRunSync()
  val sig = Signature[ECDSA].sign(h, k).unsafeRunSync()

  @Benchmark
  def sign() =
    Signature[ECDSA].sign(h, k).unsafeRunSync()

  @Benchmark
  def verify() =
    Signature[ECDSA].verify(h, sig, k.public).unsafeRunSync()

  @Benchmark
  def recover() =
    Signature[ECDSA].recoverPublic(h, sig)
}
