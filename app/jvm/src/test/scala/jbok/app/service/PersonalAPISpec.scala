package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.core.keystore.KeyStore
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.core.api.PersonalAPI

class PersonalAPISpec extends AppSpec {
  "importRawKey" in check { objects =>
    val personal   = objects.get[PersonalAPI[IO]]
    val keystore   = objects.get[KeyStore[IO]]
    val kp         = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
    val passphrase = "oho"
    for {
      _   <- personal.importRawKey(kp.secret.bytes, passphrase)
      res <- keystore.listAccounts
      _ = res.contains(Address(kp)) shouldBe true
    } yield ()
  }

  "newAccount" in check { objects =>
    val personal = objects.get[PersonalAPI[IO]]
    val keystore = objects.get[KeyStore[IO]]
    for {
      addr <- personal.newAccount("oho")
      _    <- keystore.unlockAccount(addr, "oho")
    } yield ()
  }

  "listAccounts" in check { objects =>
    val personal = objects.get[PersonalAPI[IO]]
    val keystore = objects.get[KeyStore[IO]]
    for {
      res1 <- personal.listAccounts
      res2 <- keystore.listAccounts
      _ = res1 should contain theSameElementsAs res2
    } yield ()
  }
}
