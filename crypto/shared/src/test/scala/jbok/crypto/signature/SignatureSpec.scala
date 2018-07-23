package jbok.crypto.signature

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit.ByteGen
import scodec.bits.ByteVector

class SignatureSpec extends JbokSpec {
  val messageGen = ByteGen.genBytes(32).map(x => ByteVector(x))

  def check(curve: Signature) =
    s"curve ${curve.algo}" should {

      "sign and verify for right keypair" in {
        forAll(messageGen) { message =>
          val p = for {
            kp <- curve.generateKeyPair[IO]
            sig <- curve.sign[IO](message, kp)
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
            sig <- curve.sign[IO](message, kp1)
            verify <- curve.verify[IO](message, sig, kp2.public)
          } yield verify

          p.unsafeRunSync() shouldBe false
        }
      }

      "recover keypair from secret" in {
        val keyPair = curve.generateKeyPair[IO].unsafeRunSync()
        val bytes = keyPair.secret.bytes
        val privateKey = KeyPair.Secret(bytes)
        val publicKey = curve.buildPublicKeyFromPrivate[IO](privateKey).unsafeRunSync()

        privateKey shouldBe keyPair.secret
        publicKey shouldBe keyPair.public
      }
    }
}

class RecoverableSignatureSpec extends SignatureSpec {
  def check(curve: RecoverableSignature) = {
    super.check(curve)

    s"curve ${curve.algo}" should {
      "recover public key from signature" in {
        forAll(messageGen) { message =>
          val p = for {
            kp <- curve.generateKeyPair[IO]
            sig <- curve.sign[IO](message, kp)
            verify <- curve.verify[IO](message, sig, kp.public)
            public = curve.recoverPublic(sig, message)

            _ = verify shouldBe true
            _ = public shouldBe Some(kp.public)
          } yield ()

          p.unsafeRunSync()
        }
      }
    }
  }
}