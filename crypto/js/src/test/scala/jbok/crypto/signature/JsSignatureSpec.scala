package jbok.crypto.signature

import cats.effect.IO
import jbok.common.testkit.ByteGen
import org.scalatest.{AsyncWordSpec, Matchers}

class JsSignatureSpec extends AsyncWordSpec with Matchers {
  def check(curve: Signature) =
    s"curve ${curve.algo}" should {

      val messageGen = ByteGen.genBoundedBytes(0, 1024)

      "sign and verify for right keypair" in {
        val message = messageGen.sample.get
        val p = for {
          kp <- curve.generateKeyPair[IO]
          sig <- curve.sign[IO](message, kp.secret)
          verify <- curve.verify[IO](message, sig, kp.public)
        } yield verify

        p.unsafeToFuture().map(_ shouldBe true)

      }

      "not verified for wrong keypair" in {
        val message = messageGen.sample.get
        val p = for {
          kp1 <- curve.generateKeyPair[IO]
          kp2 <- curve.generateKeyPair[IO]
          sig <- curve.sign[IO](message, kp1.secret)
          verify <- curve.verify[IO](message, sig, kp2.public)
        } yield verify

        p.unsafeToFuture().map(_ shouldBe false)
      }
    }

  check(Ed25519)
}
