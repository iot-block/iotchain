package jbok.crypto.signature

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit.ByteGen
import org.scalatest.Matchers

class SignatureSpec extends JbokSpec with Matchers {
  def check(curve: Signature) =
    s"curve ${curve.algo}" should {

      val messageGen = ByteGen.genBoundedBytes(0, 1024)

      "sign and verify for right keypair" in {
        forAll(messageGen) { message =>
          val p = for {
            kp <- curve.generateKeyPair[IO]
            sig <- curve.sign[IO](message, kp.secret)
            verify <- curve.verify[IO](message, sig, kp.public)
          } yield verify

          p.unsafeRunSync() shouldBe true
        }
      }

      "not verified for wrong keypair" in {
        forAll(messageGen) { message =>
          val p = for {
            kp1 <- curve.generateKeyPair[IO]
            kp2 <- curve.generateKeyPair[IO]
            sig <- curve.sign[IO](message, kp1.secret)
            verify <- curve.verify[IO](message, sig, kp2.public)
          } yield verify

          p.unsafeRunSync() shouldBe false
        }
      }

      "recover keypair from secret" in {
        val keyPair = curve.generateKeyPair[IO].unsafeRunSync()
        val bytes = keyPair.secret.bytes
        val privateKey = curve.buildPrivateKey[IO](bytes).unsafeRunSync()
        val publicKey = curve.buildPublicKeyFromPrivate[IO](privateKey).unsafeRunSync()

        privateKey shouldBe keyPair.secret
        publicKey shouldBe keyPair.public
      }
    }
}
