package jbok.crypto.signature

import cats.effect.IO
import jbok.crypto.signature.Curves.SHA256withSecp256k1
import tsec.keygen.asymmetric.AsymmetricKeyGen
import tsec.signature._
import tsec.signature.jca._

object Curves {
  sealed trait SHA256withSecp256k1
  object SHA256withSecp256k1 extends ECDSASignature[SHA256withSecp256k1]("SHA256withECDSA", "secp256k1", 64)
}

trait Signature[F[_], A] {
  type AKG = AsymmetricKeyGen[F, A, SigPublicKey, SigPrivateKey, SigKeyPair]

  def buildPrivateKey(bytes: Array[Byte])(implicit S: AKG): F[SigPrivateKey[A]] =
    S.buildPrivateKey(bytes)

  def buildPublicKey(bytes: Array[Byte])(implicit S: AKG): F[SigPublicKey[A]] =
    S.buildPublicKey(bytes)

  def buildKeyPair(privateKey: SigPrivateKey[A], publicKey: SigPublicKey[A]): SigKeyPair[A] =
    SigKeyPair(privateKey, publicKey)

  def generateKeyPair(implicit S: AKG): F[SigKeyPair[A]] =
    S.generateKeyPair

  def sign(toSign: Array[Byte], privateKey: SigPrivateKey[A])(
      implicit S: Signer[F, A, SigPublicKey, SigPrivateKey]): F[CryptoSignature[A]] =
    S.sign(toSign, privateKey)

  def buildSignature(bytes: Array[Byte]): CryptoSignature[A] =
    CryptoSignature(bytes)

  def verify(toSign: Array[Byte], signed: CryptoSignature[A], publicKey: SigPublicKey[A])(
      implicit S: Signer[F, A, SigPublicKey, SigPrivateKey]): F[Boolean] =
    S.verifyBool(toSign, signed, publicKey)
}

object Signatures {
  object Secp256k1Sig extends Signature[IO, SHA256withSecp256k1]
}
