package jbok.crypto.signature

import java.security.SecureRandom

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit.ByteGen
import jbok.crypto._
import scodec.bits.ByteVector

class CryptoSignatureAlgSpec extends JbokSpec {
  val messageGen = ByteGen.genBoundedBytes(0, 128).map(_.kec256)
  val random     = new SecureRandom()

  def check(s: SignatureAlg[IO]) =
    s"${s.toString}" should {
      "sign and verify for right keypair" in {
        forAll(messageGen) { message =>
          val p = for {
            kp     <- s.generateKeyPair(random)
            sig    <- s.sign(message, kp)
            verify <- s.verify(message, sig, kp.public)
          } yield verify

          p.unsafeRunSync() shouldBe true
        }
      }

      "not verified for wrong keypair" in {
        forAll(messageGen) { message =>
          val p = for {
            kp1    <- s.generateKeyPair(random)
            kp2    <- s.generateKeyPair(random)
            sig    <- s.sign(message, kp1)
            verify <- s.verify(message, sig, kp2.public)
          } yield verify

          p.unsafeRunSync() shouldBe false
        }
      }

      "generate keypair from secret" in {
        val keyPair    = s.generateKeyPair(random).unsafeRunSync()
        val bytes      = keyPair.secret.bytes
        val privateKey = KeyPair.Secret(bytes)
        val publicKey  = s.generatePublicKey(privateKey).unsafeRunSync()

        privateKey shouldBe keyPair.secret
        publicKey shouldBe keyPair.public
      }

      "roundtrip signature" in {
        forAll(messageGen) { message =>
          val p = for {
            kp  <- s.generateKeyPair(random)
            sig <- s.sign(message, kp)
            bytes = sig.bytes
            sig2  = CryptoSignature(bytes)
            verify <- s.verify(message, sig2, kp.public)
          } yield verify

          p.unsafeRunSync() shouldBe true
        }
      }
    }
}

class RecoverableCryptoSignatureAlgSpec extends JbokSpec {
  val messageGen = ByteGen.genBoundedBytes(0, 128).map(_.kec256)
  val random     = new SecureRandom()
  def check(s: RecoverableSignatureAlg[IO]) =
    s"${s.toString}" should {
      "recover public key from signature" in {
        forAll(messageGen) { message =>
          val p = for {
            kp     <- s.generateKeyPair(random)
            sig    <- s.sign(message, kp)
            verify <- s.verify(message, sig, kp.public)
            public = s.recoverPublic(message, sig)

            _ = verify shouldBe true
            _ = public shouldBe Some(kp.public)
          } yield ()

          p.unsafeRunSync()
        }
      }
    }
}
