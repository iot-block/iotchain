package jbok.core.keystore

import java.security.SecureRandom

import jbok.JbokSpec
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import jbok.crypto._
import jbok.codec.json._
import jbok.crypto.signature.KeyPair
import io.circe.syntax._

class EncryptedKeySpec extends JbokSpec {

  val gethKey =
    """{
      |  "id": "033b7a63-30f2-47fc-bbbe-d22925a14ab3",
      |  "address": "932245e1c40ec2026a2c7acc80befb68816cdba4",
      |  "crypto": {
      |    "cipher": "aes-128-ctr",
      |    "ciphertext": "8fb53f8695795d1f0480cad7954bd7a888392bb24c414b9895b4cb288b4897dc",
      |    "cipherparams": {
      |      "iv": "7a754cfd548a351aed270f6b1bfd306d"
      |    },
      |    "kdf": "scrypt",
      |    "kdfparams": {
      |      "dklen": 32,
      |      "n": 262144,
      |      "p": 1,
      |      "r": 8,
      |      "salt": "2321125eff8c3172a05a5947726004075b30e0a01534061fa5c13fb4e5e32465"
      |    },
      |    "mac": "6383677d3b0f34b1dcb9e1c767f8130daf6233266e35f28e00467af97bf2fbfa"
      |  },
      |  "version": 3
      |}
    """.stripMargin

  "EncryptedKey" should {
    "decrypt a key encrypted by Geth" in {
      val key = decode[EncryptedKey](gethKey)
      key.isRight shouldBe true
    }

    "securely store private keys" in {
      val secureRandom = new SecureRandom
      val prvKey = KeyPair.Secret(secureRandomByteString(secureRandom, 32))
      val passphrase = "P4S5W0rd"
      val encKey = EncryptedKey(prvKey, passphrase, secureRandom)

      val json = encKey.asJson.spaces2
      val decoded = decode[EncryptedKey](json)

      decoded shouldBe Right(encKey)
      decoded.flatMap(_.decrypt(passphrase)) shouldBe Right(prvKey)
    }
  }
}
