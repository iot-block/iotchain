package jbok.crypto.signature

import cats.effect.IO
import cats.implicits._
import jbok.common.CommonSpec
import jbok.crypto._
import scodec.bits.ByteVector

import scala.math.BigInt

class SignatureSpec extends CommonSpec {
  val hash = ByteVector("jbok".getBytes).kec256.toArray

  val chainId: BigInt = 61
  "ECDSA" should {
    val ecdsa = Signature[ECDSA]

    "guarantee generate keyPair length" in withIO {
      for {
        keyPair <- ecdsa.generateKeyPair[IO]()
        _ = keyPair.public.bytes.length shouldBe 64
        _ = keyPair.secret.bytes.length shouldBe 32
      } yield ()
    }

    "sign and verify for right keyPair" in withIO {

      for {
        keyPair <- ecdsa.generateKeyPair[IO]()
        signed  <- ecdsa.sign[IO](hash, keyPair, chainId)
        verify  <- ecdsa.verify[IO](hash, signed, keyPair.public, chainId)
        _ = verify shouldBe true
      } yield ()
    }

    "not verified for wrong keyPair" in withIO {
      for {
        kp1    <- ecdsa.generateKeyPair[IO]()
        kp2    <- ecdsa.generateKeyPair[IO]()
        sig    <- ecdsa.sign[IO](hash, kp1, chainId)
        verify <- ecdsa.verify[IO](hash, sig, kp2.public, chainId)
        _ = verify shouldBe false
      } yield ()
    }

    "generate keyPair from secret" in withIO {
      for {
        keyPair <- ecdsa.generateKeyPair[IO]()
        bytes      = keyPair.secret.bytes
        privateKey = KeyPair.Secret(bytes)
        publicKey <- ecdsa.generatePublicKey[IO](privateKey)
        _ = privateKey shouldBe keyPair.secret
        _ = publicKey shouldBe keyPair.public
      } yield ()
    }

    "roundtrip signature" in withIO {
      for {
        kp  <- ecdsa.generateKeyPair[IO]()
        sig <- ecdsa.sign[IO](hash, kp, chainId)
        bytes = sig.bytes
        sig2  = CryptoSignature(bytes)
        verify <- ecdsa.verify[IO](hash, sig2, kp.public, chainId)
        _ = verify shouldBe true
      } yield ()
    }

    "recover public key from signature" in withIO {
      for {
        kp     <- ecdsa.generateKeyPair[IO]()
        sig    <- ecdsa.sign[IO](hash, kp, chainId)
        verify <- ecdsa.verify[IO](hash, sig, kp.public, chainId)
        public = ecdsa.recoverPublic(hash, sig, chainId)

        _ = verify shouldBe true
        _ = public shouldBe Some(kp.public)
      } yield ()
    }

    "verify signature to false if different chainId" in withIO {
      for {
        kp  <- ecdsa.generateKeyPair[IO]()
        sig <- ecdsa.sign[IO](hash, kp, chainId)
        res <- ecdsa.verify[IO](hash, sig, kp.public, chainId)
        _ = res shouldBe true
        res <- ecdsa.verify[IO](hash, sig, kp.public, chainId + 1)
        _ = res shouldBe false
      } yield ()
    }

    "recover public key to None if different chainId" in withIO {
      for {
        kp  <- ecdsa.generateKeyPair[IO]()
        sig <- ecdsa.sign[IO](hash, kp, chainId)
        res1 = ecdsa.recoverPublic(hash, sig, chainId)
        _    = res1 shouldBe Some(kp.public)
        res2 = ecdsa.recoverPublic(hash, sig, chainId + 1)
        _    = res2 shouldBe None
      } yield ()
    }

    "encrypt message by known secret key" in withIO {
      for {
        secret <- KeyPair
          .Secret("0xcfb8493e50c4aacda5813b2b48f13b4af17106993dbf142877e2e346dfc40668")
          .pure[IO]
        public <- ecdsa.generatePublicKey[IO](secret)
        keyPair = KeyPair(public, secret)
        sig <- ecdsa.sign[IO]("Actions speak louder than words.".getBytes, keyPair, 0)

        _ = sig.bytes shouldBe ByteVector
          .fromValidHex("0xe0f71d96ea314543db806aaa63179fc08abac87b7c43ec3b27395dd8b45512db372572d08384c1c777d95548c8e35334f4f7de0f70909fb3c644b8f98b9851601c")
          .toArray
      } yield ()
    }
  }
}
