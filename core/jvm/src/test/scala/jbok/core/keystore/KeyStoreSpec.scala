package jbok.core.keystore

import cats.effect.IO
import jbok.core.{CoreModule, CoreSpec}
import jbok.core.keystore.KeyStoreError._
import jbok.core.models.Address
import jbok.crypto.signature.KeyPair
import scodec.bits._

class KeyStoreSpec extends CoreSpec {
  val key1  = hex"7a44789ed3cd85861c0bbf9693c7e1de1862dd4396c390147ecf1275099c6e6f"
  val addr1 = Address(hex"aa6826f00d01fe4085f0c3dd12778e206ce4e2ac")

  "KeyStore" should {
    "import and list accounts" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        listBeforeImport <- keyStore.listAccounts
        _ = listBeforeImport shouldBe Nil
        // We sleep between imports so that dates of key files' names are different
        res1 <- keyStore.importPrivateKey(key1, "aaa")
        _ = res1 shouldBe addr1
        listAfterImport <- keyStore.listAccounts
        // result should be ordered by creation date
        _ = listAfterImport shouldBe List(addr1)
      } yield ()
    }

    "create new accounts" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        newAddr1          <- keyStore.newAccount("aaa")
        newAddr2          <- keyStore.newAccount("bbb")
        listOfNewAccounts <- keyStore.listAccounts
        _ = listOfNewAccounts.toSet shouldBe Set(newAddr1, newAddr2)
        _ = listOfNewAccounts.length shouldBe 2
      } yield ()
    }

    "unlock an account provided a correct passphrase" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      val passphrase = "aaa"
      for {
        _      <- keyStore.importPrivateKey(key1, passphrase)
        wallet <- keyStore.unlockAccount(addr1, passphrase)
        _ = wallet.address shouldBe addr1
        _ = wallet.keyPair.secret shouldBe KeyPair.Secret(key1)
      } yield ()
    }

    "return an error when unlocking an account with a wrong passphrase" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        _   <- keyStore.importPrivateKey(key1, "aaa")
        res <- keyStore.unlockAccount(addr1, "bbb").attempt
        _ = res shouldBe Left(DecryptionFailed)
      } yield ()
    }

    "return an error when trying to unlock an unknown account" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        res <- keyStore.unlockAccount(addr1, "bbb").attempt
        _ = res shouldBe Left(KeyNotFound)
      } yield ()
    }

    "return an error deleting not existing wallet" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        res <- keyStore.deleteAccount(addr1).attempt
        _ = res shouldBe Left(KeyNotFound)
      } yield ()
    }

    "delete existing wallet " in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        newAddr1          <- keyStore.newAccount("aaa")
        listOfNewAccounts <- keyStore.listAccounts
        _ = listOfNewAccounts.toSet shouldBe Set(newAddr1)
        res <- keyStore.deleteAccount(newAddr1)
        _ = res shouldBe true
        listOfNewAccountsAfterDelete <- keyStore.listAccounts
        _ = listOfNewAccountsAfterDelete.toSet shouldBe Set.empty
      } yield ()
    }

    "change passphrase of an existing wallet" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      val oldPassphrase = "weakpass"
      val newPassphrase = "very5tr0ng&l0ngp4s5phr4s3"
      for {
        _   <- keyStore.importPrivateKey(key1, oldPassphrase)
        res <- keyStore.changePassphrase(addr1, oldPassphrase, newPassphrase)
        _ = res shouldBe true
        wallet <- keyStore.unlockAccount(addr1, newPassphrase)
        _ = wallet.address shouldBe addr1
        _ = wallet.keyPair.secret shouldBe KeyPair.Secret(key1)
      } yield ()
    }

    "return an error when changing passphrase of an non-existent wallet" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        res <- keyStore.changePassphrase(addr1, "oldpass", "newpass").attempt
        _ = res shouldBe Left(KeyNotFound)
      } yield ()
    }

    "return an error when changing passphrase and provided with invalid old passphrase" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        _   <- keyStore.importPrivateKey(key1, "oldpass")
        res <- keyStore.changePassphrase(addr1, "wrongpass", "newpass").attempt
        _ = res shouldBe Left(DecryptionFailed)
      } yield ()
    }

    "import private key with empty passphrase and unlock account" in check { objects =>
      val keyStore = objects.get[KeyStore[IO]]
      for {
        _   <- keyStore.importPrivateKey(key1, "")
        res <- keyStore.unlockAccount(addr1, "").attempt
        _ = res.isRight shouldBe true
      } yield ()
    }
  }
}
