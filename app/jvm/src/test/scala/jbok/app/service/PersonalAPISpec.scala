package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.core.keystore.KeyStore
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.core.api.PersonalAPI

class PersonalAPISpec extends AppSpec {
  val objects = locator.unsafeRunSync()
  val personal = objects.get[PersonalAPI[IO]]
  val keystore = objects.get[KeyStore[IO]]

  "importRawKey" in {
    val kp = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
    val passphrase = "oho"
    personal.importRawKey(kp.secret.bytes, passphrase).unsafeRunSync()
    keystore.listAccounts.unsafeRunSync() shouldBe List(Address(kp))
  }

  "newAccount" in {
    val addr = personal.newAccount("oho").unsafeRunSync()
    keystore.unlockAccount(addr, "oho").unsafeRunSync()
  }

//  def delAccount(address: Address): F[Boolean]
//
//  def listAccounts: F[List[Address]]
  "listAccounts" in {
    personal.listAccounts.unsafeRunSync() should contain theSameElementsAs keystore.listAccounts.unsafeRunSync()
  }
//
//  def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): F[Boolean]
//
//  def lockAccount(address: Address): F[Boolean]
//
//  def sign(message: ByteVector, address: Address, passphrase: Option[String]): F[CryptoSignature]
//
//  def ecRecover(message: ByteVector, signature: CryptoSignature): F[Address]
//
//  def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): F[ByteVector]
//
//  def deleteWallet(address: Address): F[Boolean]
//
//  def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Boolean]
}
