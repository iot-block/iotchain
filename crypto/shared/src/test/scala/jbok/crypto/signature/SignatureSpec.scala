package jbok.crypto.signature


import jbok.JbokAsyncSpec
import jbok.crypto._

class SignatureSpec extends JbokAsyncSpec {
  val hash = "jbok".utf8bytes.kec256.toArray

  "ECDSA" should {
    val ecdsa = Signature[ECDSA]

    "sign and verify for right keypair" in {

      for {
        keyPair <- ecdsa.generateKeyPair()
        signed  <- ecdsa.sign(hash, keyPair)
        verify  <- ecdsa.verify(hash, signed, keyPair.public)
        _ = verify shouldBe true
      } yield ()
    }

    "not verified for wrong keypair" in {
      for {
        kp1    <- ecdsa.generateKeyPair()
        kp2    <- ecdsa.generateKeyPair()
        sig    <- ecdsa.sign(hash, kp1)
        verify <- ecdsa.verify(hash, sig, kp2.public)
        _ = verify shouldBe false
      } yield ()
    }

    "generate keypair from secret" in {
      for {
        keyPair <- ecdsa.generateKeyPair()
        bytes      = keyPair.secret.bytes
        privateKey = KeyPair.Secret(bytes)
        publicKey <- ecdsa.generatePublicKey(privateKey)
        _ = privateKey shouldBe keyPair.secret
        _ = publicKey shouldBe keyPair.public
      } yield ()
    }

    "roundtrip signature" in {
      for {
        kp  <- ecdsa.generateKeyPair()
        sig <- ecdsa.sign(hash, kp)
        bytes = sig.bytes
        sig2  = CryptoSignature(bytes)
        verify <- ecdsa.verify(hash, sig2, kp.public)
        _ = verify shouldBe true
      } yield ()
    }

    "recover public key from signature" in {
      for {
        kp     <- ecdsa.generateKeyPair()
        sig    <- ecdsa.sign(hash, kp)
        verify <- ecdsa.verify(hash, sig, kp.public)
        public = ecdsa.recoverPublic(hash, sig)

        _ = verify shouldBe true
        _ = public shouldBe Some(kp.public)
      } yield ()
    }
  }
}
