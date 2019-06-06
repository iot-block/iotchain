package jbok.crypto

import java.security.SecureRandom

import cats.effect.IO
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import org.scalacheck.{Arbitrary, Gen}

object testkit {
  def genKeyPair: Gen[KeyPair] =
    Signature[ECDSA].generateKeyPair[IO](Some(new SecureRandom())).unsafeRunSync()

  implicit def arbKeyPair: Arbitrary[KeyPair] = Arbitrary {
    genKeyPair
  }
}
