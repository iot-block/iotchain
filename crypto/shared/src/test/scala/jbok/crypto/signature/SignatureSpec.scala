package jbok.crypto.signature

import cats.effect.IO
import jbok.JbokAsyncSpec
import jbok.crypto._

class SignatureSpec extends JbokAsyncSpec {
  val hash = "jbok".utf8bytes.kec256.toArray

  val chainId: BigInt = 61
  "ECDSA" should {
    val ecdsa = Signature[ECDSA]

    "guarantee generate keypair length" in {
      for {
        keyPair <- ecdsa.generateKeyPair[IO]()
        _ = keyPair.public.bytes.length shouldBe 64
        _ = keyPair.secret.bytes.length shouldBe 32
      } yield ()
    }

    "sign and verify for right keypair" in {

      for {
        keyPair <- ecdsa.generateKeyPair[IO]()
        signed  <- ecdsa.sign[IO](hash, keyPair, chainId)
        verify  <- ecdsa.verify[IO](hash, signed, keyPair.public, chainId)
        _ = verify shouldBe true
      } yield ()
    }

    "not verified for wrong keypair" in {
      for {
        kp1    <- ecdsa.generateKeyPair[IO]()
        kp2    <- ecdsa.generateKeyPair[IO]()
        sig    <- ecdsa.sign[IO](hash, kp1, chainId)
        verify <- ecdsa.verify[IO](hash, sig, kp2.public, chainId)
        _ = verify shouldBe false
      } yield ()
    }

    "generate keypair from secret" in {
      for {
        keyPair <- ecdsa.generateKeyPair[IO]()
        bytes      = keyPair.secret.bytes
        privateKey = KeyPair.Secret(bytes)
        publicKey <- ecdsa.generatePublicKey[IO](privateKey)
        _ = privateKey shouldBe keyPair.secret
        _ = publicKey shouldBe keyPair.public
      } yield ()
    }

    "roundtrip signature" in {
      for {
        kp  <- ecdsa.generateKeyPair[IO]()
        sig <- ecdsa.sign[IO](hash, kp, chainId)
        bytes = sig.bytes
        sig2  = CryptoSignature(bytes)
        verify <- ecdsa.verify[IO](hash, sig2, kp.public, chainId)
        _ = verify shouldBe true
      } yield ()
    }

    "recover public key from signature" in {
      for {
        kp     <- ecdsa.generateKeyPair[IO]()
        sig    <- ecdsa.sign[IO](hash, kp, chainId)
        verify <- ecdsa.verify[IO](hash, sig, kp.public, chainId)
        public = ecdsa.recoverPublic(hash, sig, chainId)

        _ = verify shouldBe true
        _ = public shouldBe Some(kp.public)
      } yield ()
    }
  }
}
