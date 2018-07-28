package jbok.crypto.cipher

import java.nio.charset.StandardCharsets

import cats.effect.IO
import jbok.JbokSpec
import scodec.bits._
import tsec.cipher.symmetric.jca._
import tsec.cipher.symmetric.{CipherText, Iv, PlainText}

class AesSpec extends JbokSpec {

  "AES-CBC" should {

    implicit val encryptor = AES128CBC.encryptor[IO].unsafeRunSync()

    "correctly evaluate for the test vectors" in {
      // https://tools.ietf.org/html/rfc3602#section-4
      val testVectors = Table[String, String, ByteVector, String](
        ("key", "iv", "plaintext", "ciphertext"),
        ("06a9214036b8a15b512e03d534120006",
         "3dafba429d9eb430b422da802c9fac41",
         ByteVector("Single block msg".getBytes(StandardCharsets.US_ASCII)),
         "e353779c1079aeb82708942dbe77181a"),
        ("c286696d887c9aa0611bbb3e2025a45a",
         "562e17996d093d28ddb3ba695a2e6f58",
         hex"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
         "d296cd94c2cccf8a3a863028b5e1dc0a7586602d253cfff91b8266bea6d61ab1"),
        ("6c3ea0477630ce21a2ce334aa746c2cd",
         "c782dc4c098c66cbd9cd27d825682c81",
         ByteVector("This is a 48-byte message (exactly 3 AES blocks)".getBytes(StandardCharsets.US_ASCII)),
         "d0a02b3836451753d493665d33f0e8862dea54cdb293abc7506939276772f8d5021c19216bad525c8579695d83ba2684"),
        ("56e47a38c5598974bc46903dba290349",
         "8ce82eefbea0da3c44699ed7db51b7d9",
         hex"a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf",
         "c30e32ffedc0774e6aff6af0869f71aa0f3af07a9a31a9c684db207eb0ef8e4e35907aa632c3ffdf868bb7b29d3d46ad83ce9f9a102ee99d49a53e87f4c3da55")
      )

      forAll(testVectors) {
        case (k, i, plaintext, c) =>
          val key = ByteVector.fromValidHex(k)
          val iv = ByteVector.fromValidHex(i)
          val ciphertext = ByteVector.fromValidHex(c)
          val jcaKey = AES128CBC.buildKey[IO](key.toArray).unsafeRunSync()
          val encrypted =
            AES128CBC.encrypt[IO](PlainText(plaintext.toArray), jcaKey, Iv[AES128CBC](iv.toArray)).unsafeRunSync()
          val decrypted = AES128CBC.decrypt[IO](encrypted, jcaKey).unsafeRunSync()

          decrypted shouldBe PlainText(plaintext.toArray)
      }
    }
  }

  "AES-CTR" should {
    "correctly evaluate for the test vectors" in {
      // http://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38a.pdf Appendix F.5
      val testVectors = Table[String, String, String, String](
        ("key", "iv", "plaintext", "ciphertext"),

        ("2b7e151628aed2a6abf7158809cf4f3c",
          "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
          "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
          "874d6191b620e3261bef6864990db6ce9806f66b7970fdff8617187bb9fffdff5ae4df3edbd5d35e5b4f09020db03eab1e031dda2fbe03d1792170a0f3009cee")
//
//        ("8e73b0f7da0e6452c810f32b809079e562f8ead2522c6b7b",
//          "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
//          "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
//          "1abc932417521ca24f2b0459fe7e6e0b090339ec0aa6faefd5ccc2c6f4ce8e941e36b26bd1ebc670d1bd1d665620abf74f78a7f6d29809585a97daec58c6b050"),
//
//        ("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4",
//          "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
//          "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
//          "601ec313775789a5b7a7f504bbf3d228f443e3ca4d62b59aca84e990cacaf5c52b0930daa23de94ce87017ba2d84988ddfc9c58db67aada613c2dd08457941a6")
      )

      implicit val encryptor = AES128CTR.genEncryptor[IO].unsafeRunSync()

      forAll(testVectors) { (k, i, p, c) =>
        val key = ByteVector.fromValidHex(k)
        val iv = ByteVector.fromValidHex(i)
        val plaintext = ByteVector.fromValidHex(p)
        val ciphertext = ByteVector.fromValidHex(c)
        val jcaKey = AES128CTR.buildKey[IO](key.toArray).unsafeRunSync()

        val encrypted = AES128CTR.encrypt[IO](PlainText(plaintext.toArray), jcaKey ,Iv[AES128CTR](iv.toArray)).unsafeRunSync()
        AES128CTR.decrypt[IO](encrypted, jcaKey).unsafeRunSync() shouldBe PlainText(plaintext.toArray)
      }
    }
  }
}
