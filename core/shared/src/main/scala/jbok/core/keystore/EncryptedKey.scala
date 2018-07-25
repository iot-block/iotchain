package jbok.core.keystore

import java.security.SecureRandom
import java.util.UUID

import cats.effect.IO
import jbok.core.models.Address
import jbok.crypto._
import jbok.crypto.password.SCrypt
import jbok.crypto.signature.{KeyPair, SecP256k1}
import scodec.bits.ByteVector
import tsec.cipher.symmetric._
import tsec.cipher.symmetric.jca._

case class KdfParams(salt: ByteVector, n: Int, r: Int, p: Int, dklen: Int)

case class CipherParams(iv: ByteVector)
case class CryptoSpec(
    cipher: String,
    ciphertext: ByteVector,
    cipherparams: CipherParams,
    kdf: String,
    kdfparams: KdfParams,
    mac: ByteVector
)

import jbok.core.keystore.EncryptedKey._

case class EncryptedKey(
    id: UUID,
    address: Address,
    crypto: CryptoSpec,
    version: Int
) {
  implicit val encryptor = AES128CTR.genEncryptor[IO].unsafeRunSync()

  def decrypt(passphrase: String): Either[String, KeyPair.Secret] = {
    val dk = deriveKey(passphrase, crypto.kdfparams)
    val secret = dk.take(16)
    val content = RawCipherText[AES128CTR](crypto.ciphertext.toArray)
    val nonce = Iv[AES128CTR](crypto.cipherparams.iv.toArray)
    val cipherText = CipherText[AES128CTR](content, nonce)
    val jcaKey = AES128CTR.buildKey[IO](secret.toArray).unsafeRunSync()
    val decrypted = AES128CTR.decrypt[IO](cipherText, jcaKey).attempt.map {
      case Left(_) => Left("Couldn't decrypt key")
      case Right(plainText) =>
        if (createMac(dk, ByteVector(cipherText.content)) == crypto.mac) {
          Right(KeyPair.Secret(ByteVector(plainText)))
        } else {
          Left("Couldn't decrypt key with given passphrase")
        }
    }

    decrypted.unsafeRunSync()
  }
}

object EncryptedKey {
  implicit val encryptor = AES128CTR.genEncryptor[IO].unsafeRunSync()

  def apply(prvKey: KeyPair.Secret, passphrase: String, secureRandom: SecureRandom): EncryptedKey = {
    val version = 3
    val uuid = UUID.randomUUID()
    val pubKey = SecP256k1.buildPublicKeyFromPrivate[IO](prvKey).unsafeRunSync()
    val address = Address(KeyPair(pubKey, prvKey))
    val salt = secureRandomByteString(secureRandom, 32)
    val kdfParams = KdfParams(salt, 1 << 18, 8, 1, 32) //params used by Geth
    val dk = deriveKey(passphrase, kdfParams)
    val secret = dk.take(16)

    val jcaKey = AES128CTR.buildKey[IO](secret.toArray).unsafeRunSync()
    val iv = JCAIvGen.random[IO, AES128CTR]
    val cipherText =
      AES128CTR.encrypt[IO](PlainText(prvKey.bytes.toArray), jcaKey, iv)(encryptor).unsafeRunSync()
    val cipherContent = ByteVector(cipherText.content)
    val mac = createMac(dk, cipherContent)

    val cryptoSpec =
      CryptoSpec("aes-128-ctr", cipherContent, CipherParams(ByteVector(cipherText.nonce)), "scrypt", kdfParams, mac)
    EncryptedKey(uuid, address, cryptoSpec, version)
  }

  private def deriveKey(passphrase: String, kdfParams: KdfParams): ByteVector =
    kdfParams match {
      case KdfParams(salt, n, r, p, dkLen) =>
        SCrypt.derive(passphrase, salt, n, r, p, dkLen)

//      case Pbkdf2Params(salt, prf, c, dklen) =>
//        // prf is currently ignored, only hmac sha256 is used
//        crypto.pbkdf2HMacSha256(passphrase, salt, c, dklen)
    }

  private def createMac(dk: ByteVector, ciphertext: ByteVector): ByteVector =
    (dk.slice(16, 32) ++ ciphertext).kec256

}
