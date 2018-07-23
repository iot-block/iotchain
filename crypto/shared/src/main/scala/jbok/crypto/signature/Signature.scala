package jbok.crypto.signature

import cats.effect.Sync
import scodec.bits.ByteVector

trait Signature {
  val algo: String

  def buildPublicKeyFromPrivate[F[_]: Sync](secret: KeyPair.Secret): F[KeyPair.Public]

  def generateKeyPair[F[_]](implicit F: Sync[F]): F[KeyPair]

  def sign[F[_]](hash: ByteVector, keyPair: KeyPair, chainId: Option[Byte] = None)(
      implicit F: Sync[F]): F[CryptoSignature]

  def verify[F[_]](hash: ByteVector, signed: CryptoSignature, pk: KeyPair.Public)(implicit F: Sync[F]): F[Boolean]
}

trait RecoverableSignature extends Signature {
  def recoverPublic(sig: CryptoSignature, hash: ByteVector, chainId: Option[Byte] = None): Option[KeyPair.Public]
}
