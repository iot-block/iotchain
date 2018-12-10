package jbok.crypto.signature

import java.util.Random

import cats.effect.Sync

sealed trait ECDSA

trait Signature[A] {
  def generateKeyPair[F[_]](random: Option[Random] = None)(implicit F: Sync[F]): F[KeyPair]

  def generatePublicKey[F[_]](secret: KeyPair.Secret)(implicit F: Sync[F]): F[KeyPair.Public]

  def sign[F[_]](hash: Array[Byte], keyPair: KeyPair, chainId: BigInt)(implicit F: Sync[F]): F[CryptoSignature]

  def verify[F[_]](hash: Array[Byte], sig: CryptoSignature, public: KeyPair.Public, chainId: BigInt)(
      implicit F: Sync[F]): F[Boolean]

  def recoverPublic(hash: Array[Byte], sig: CryptoSignature, chainId: BigInt): Option[KeyPair.Public]
}

object Signature {
  def apply[A](implicit ev: Signature[A]): Signature[A] = ev
}
