package jbok.benchmark

import cats.effect.IO
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, Signature}
import org.openjdk.jmh.annotations.{Benchmark, OperationsPerInvocation}
import fs2._

class SignatureBenchmark extends JbokBenchmark {
  val s               = "hash benchmark"
  val b               = s.utf8bytes
  val h               = b.kec256.toArray
  val k               = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
  val chainId: BigInt = 1
  val sig             = Signature[ECDSA].sign[IO](h, k, chainId).unsafeRunSync()

  @Benchmark
  @OperationsPerInvocation(100)
  def signSecp256k1() =
    (0 until 100).foreach(_ => Signature[ECDSA].sign[IO](h, k, chainId).unsafeRunSync())

  @Benchmark
  @OperationsPerInvocation(100)
  def signSecp256k1Parallel() =
    Stream
      .range(0, 100)
      .covary[IO]
      .mapAsyncUnordered(4)(_ => Signature[ECDSA].sign[IO](h, k, chainId))
      .compile
      .drain
      .unsafeRunSync()

  @Benchmark
  def verifySecp256k1() =
    Signature[ECDSA].verify[IO](h, sig, k.public, chainId).unsafeRunSync()

  @Benchmark
  def recoverSecp256k1() =
    Signature[ECDSA].recoverPublic(h, sig, chainId)

}
