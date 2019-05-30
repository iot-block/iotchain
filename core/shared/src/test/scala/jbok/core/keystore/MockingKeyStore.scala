package jbok.core.keystore

import cats.effect.Sync
import cats.implicits._
import cats.effect.concurrent.Ref
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits.ByteVector

final class MockingKeyStore[F[_]](implicit F: Sync[F]) extends KeyStore[F] {
  private val m: Ref[F, Map[Address, KeyPair]] = Ref.unsafe(Map.empty)

  override def newAccount(passphrase: String): F[Address] =
    for {
      kp <- Signature[ECDSA].generateKeyPair[F]()
      _  <- m.update(_ + (Address(kp) -> kp))
    } yield Address(kp)

  override def readPassphrase(prompt: String): F[String] =
    ???

  override def importPrivateKey(key: ByteVector, passphrase: String): F[Address] =
    for {
      secret <- KeyPair.Secret(key).pure[F]
      public <- Signature[ECDSA].generatePublicKey[F](secret)
      kp      = KeyPair(public, secret)
      address = Address(kp)
      _ <- m.update(_ + (address -> kp))
    } yield address

  override def listAccounts: F[List[Address]] =
    m.get.map(_.keys.toList)

  override def unlockAccount(address: Address, passphrase: String): F[Wallet] =
    m.get.map(_(address)).map { kp =>
      Wallet(Address(kp), kp)
    }

  override def deleteAccount(address: Address): F[Boolean] =
    m.update(_ - address).as(true)

  override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Boolean] =
    F.pure(true)
}

object MockingKeyStore {
  def withInitKeys[F[_]: Sync](initKeys: List[KeyPair]): F[MockingKeyStore[F]] = {
    val keystore = new MockingKeyStore[F]()
    initKeys.traverse(kp => keystore.importPrivateKey(kp.secret.bytes, "")).as(keystore)
  }
}
