package jbok.crypto.signature

import jbok.crypto.signature.Signatures.Secp256k1Sig
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}
import tsec.common._
import tsec.signature.jca._

class SignaturesTest extends FunSuite with Matchers with PropertyChecks {
  test("same keys") {
    forAll { s: String =>
      val toSign = s.utf8Bytes
      val program = for {
        keypair <- Secp256k1Sig.generateKeyPair
        signed <- Secp256k1Sig.sign(toSign, keypair.privateKey)
        verify <- Secp256k1Sig.verify(toSign, signed, keypair.publicKey)
      } yield verify

      program.unsafeRunSync() shouldBe true
    }
  }

  test("different keys") {
    forAll { s: String =>
      val toSign = s.utf8Bytes
      val program = for {
        keypair1 <- Secp256k1Sig.generateKeyPair
        keypair2 <- Secp256k1Sig.generateKeyPair
        signed <- Secp256k1Sig.sign(toSign, keypair1.privateKey)
        verify <- Secp256k1Sig.verify(toSign, signed, keypair2.publicKey)
      } yield verify

      program.unsafeRunSync() shouldBe false
    }
  }
}
