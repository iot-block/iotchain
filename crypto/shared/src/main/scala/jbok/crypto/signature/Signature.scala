package jbok.crypto.signature

import java.util.Random

import cats.effect.IO

sealed trait ECDSA

trait Signature[F[_], A] {
  def generateKeyPair(random: Option[Random] = None): F[KeyPair]

  def generatePublicKey(secret: KeyPair.Secret): F[KeyPair.Public]

  def sign(hash: Array[Byte], keyPair: KeyPair, chainId: Option[Byte] = None): F[CryptoSignature]

  def verify(hash: Array[Byte], sig: CryptoSignature, public: KeyPair.Public): F[Boolean]

  def recoverPublic(hash: Array[Byte], sig: CryptoSignature, chainId: Option[Byte] = None): Option[KeyPair.Public]
}

object Signature {
  def apply[A](implicit ev: Signature[IO, A]): Signature[IO, A] = ev
}
