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
  def signSecp256k1() =
    Signature[ECDSA].sign(h, k).unsafeRunSync()

  @Benchmark
  def verifySecp256k1() =
    Signature[ECDSA].verify(h, sig, k.public).unsafeRunSync()

  @Benchmark
  def recoverSecp256k1() =
    Signature[ECDSA].recoverPublic(h, sig)
}
