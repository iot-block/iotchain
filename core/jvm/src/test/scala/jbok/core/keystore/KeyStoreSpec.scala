package jbok.core.keystore

import cats.effect.IO
import jbok.core.CoreSpec
import jbok.core.keystore.KeyStoreError._
import jbok.core.models.Address
import jbok.crypto.signature.KeyPair
import scodec.bits._

class KeyStoreSpec extends CoreSpec {
  val key1     = hex"7a44789ed3cd85861c0bbf9693c7e1de1862dd4396c390147ecf1275099c6e6f"
  val addr1    = Address(hex"aa6826f00d01fe4085f0c3dd12778e206ce4e2ac")
  val objects = locator.unsafeRunSync()
  val keyStore = objects.get[KeyStore[IO]]

  "KeyStore" should {
    "import and list accounts" in {
      val listBeforeImport = keyStore.listAccounts.unsafeRunSync()
      listBeforeImport shouldBe Nil

      // We sleep between imports so that dates of key files' names are different
      val res1 = keyStore.importPrivateKey(key1, "aaa").unsafeRunSync()

      res1 shouldBe addr1

      val listAfterImport = keyStore.listAccounts.unsafeRunSync()
      // result should be ordered by creation date
      listAfterImport shouldBe List(addr1)
    }

    "create new accounts" in {
      val newAddr1 = keyStore.newAccount("aaa").unsafeRunSync()
      val newAddr2 = keyStore.newAccount("bbb").unsafeRunSync()

      val listOfNewAccounts = keyStore.listAccounts.unsafeRunSync()
      listOfNewAccounts.toSet shouldBe Set(newAddr1, newAddr2)
      listOfNewAccounts.length shouldBe 2
    }

    "unlock an account provided a correct passphrase" in {
      val passphrase = "aaa"
      keyStore.importPrivateKey(key1, passphrase).unsafeRunSync()
      val wallet = keyStore.unlockAccount(addr1, passphrase).unsafeRunSync()
      wallet.address shouldBe addr1
      wallet.keyPair.secret shouldBe KeyPair.Secret(key1)
    }

    "return an error when unlocking an account with a wrong passphrase" in {
      keyStore.importPrivateKey(key1, "aaa").unsafeRunSync()
      val res = keyStore.unlockAccount(addr1, "bbb").attempt.unsafeRunSync()
      res shouldBe Left(DecryptionFailed)
    }

    "return an error when trying to unlock an unknown account" in {
      val res = keyStore.unlockAccount(addr1, "bbb").attempt.unsafeRunSync()
      res shouldBe Left(KeyNotFound)
    }

    "return an error deleting not existing wallet" in {
      val res = keyStore.deleteAccount(addr1).attempt.unsafeRunSync()
      res shouldBe Left(KeyNotFound)
    }

    "delete existing wallet " in {
      val newAddr1          = keyStore.newAccount("aaa").unsafeRunSync()
      val listOfNewAccounts = keyStore.listAccounts.unsafeRunSync()
      listOfNewAccounts.toSet shouldBe Set(newAddr1)

      val res = keyStore.deleteAccount(newAddr1).unsafeRunSync()
      res shouldBe true

      val listOfNewAccountsAfterDelete = keyStore.listAccounts.unsafeRunSync()
      listOfNewAccountsAfterDelete.toSet shouldBe Set.empty
    }

    "change passphrase of an existing wallet" in {
      val oldPassphrase = "weakpass"
      val newPassphrase = "very5tr0ng&l0ngp4s5phr4s3"

      keyStore.importPrivateKey(key1, oldPassphrase).unsafeRunSync()
      keyStore.changePassphrase(addr1, oldPassphrase, newPassphrase).unsafeRunSync() shouldBe true
      val wallet = keyStore.unlockAccount(addr1, newPassphrase).unsafeRunSync()
      wallet.address shouldBe addr1
      wallet.keyPair.secret shouldBe KeyPair.Secret(key1)
    }

    "return an error when changing passphrase of an non-existent wallet" in {
      keyStore.changePassphrase(addr1, "oldpass", "newpass").attempt.unsafeRunSync() shouldBe Left(KeyNotFound)
    }

    "return an error when changing passphrase and provided with invalid old passphrase" in {
      keyStore.importPrivateKey(key1, "oldpass").unsafeRunSync()
      keyStore.changePassphrase(addr1, "wrongpass", "newpass").attempt.unsafeRunSync() shouldBe Left(DecryptionFailed)
    }

    "import private key with empty passphrase and unlock account" in {
      keyStore.importPrivateKey(key1, "").unsafeRunSync()
      keyStore.unlockAccount(addr1, "").attempt.unsafeRunSync().isRight shouldBe true
    }
  }

  override protected def afterEach(): Unit =
    keyStore.clear.unsafeRunSync()
}
