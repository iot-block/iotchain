package jbok.crypto.signature

import cats.effect.Sync

trait Signature {
  val algo: String

  val seedBits: Int

  def buildPrivateKey[F[_]: Sync](bytes: Array[Byte]): F[KeyPair.Secret]

  def buildPublicKeyFromPrivate[F[_]: Sync](secret: KeyPair.Secret): F[KeyPair.Public]

  def generateKeyPair[F[_]](implicit F: Sync[F]): F[KeyPair]

  def sign[F[_]](toSign: Array[Byte], sk: KeyPair.Secret)(implicit F: Sync[F]): F[CryptoSignature]

  def verify[F[_]](toSign: Array[Byte], signed: CryptoSignature, pk: KeyPair.Public)(implicit F: Sync[F]): F[Boolean]
}
