package jbok.core.models

import cats.effect.{IO, Sync}
import jbok.core.CoreSpec
import jbok.core.testkit._
import jbok.core.validators.TxValidator
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, Signature}

class SignedTransactionSpec extends CoreSpec {
  "SignedTransaction" should {
    "correctly set pointSign for chainId with chain specific signing schema" in {
      forAll { tx: Transaction =>
        val keyPair                  = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
        implicit val chainId: BigInt = 61
        val address                  = Address(keyPair.public.bytes.kec256)
        val result                   = SignedTransaction.sign[IO](tx, keyPair).unsafeRunSync()
        val senderAddress            = result.senderAddress.getOrElse(Address.empty)
        address shouldBe senderAddress
      }
    }

    "respect chainId" in {
      forAll { tx: Transaction =>
        val keyPair                  = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
        implicit val chainId: BigInt = config.genesis.chainId
        val stx                      = SignedTransaction.sign[IO](tx, keyPair).unsafeRunSync()
        TxValidator.checkSyntacticValidity[IO](stx, chainId).attempt.unsafeRunSync().isRight shouldBe true

        val stx2 = SignedTransaction.sign[IO](tx, keyPair)(Sync[IO], chainId + 1).unsafeRunSync()
        TxValidator.checkSyntacticValidity[IO](stx2, chainId).attempt.unsafeRunSync().isLeft shouldBe true
      }
    }
  }
}
