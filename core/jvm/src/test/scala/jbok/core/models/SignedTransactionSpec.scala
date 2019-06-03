package jbok.core.models

import cats.effect.IO
import jbok.core.CoreSpec
import jbok.core.validators.TxValidator
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, Signature}

class SignedTransactionSpec extends CoreSpec {
  "SignedTransaction" should {
    "correctly set pointSign for chainId with chain specific signing schema" in {
      forAll { tx: Transaction =>
        val keyPair          = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
        val chainId: ChainId = ChainId(61)
        val address          = Address(keyPair.public.bytes.kec256)
        val result           = SignedTransaction.sign[IO](tx, keyPair, chainId).unsafeRunSync()
        val senderAddress    = result.senderAddress.getOrElse(Address.empty)
        address shouldBe senderAddress
        Long.MaxValue
      }
    }

    "respect chainId" in {
      forAll { tx: Transaction =>
        val keyPair          = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
        val chainId: ChainId = config.genesis.chainId
        val stx              = SignedTransaction.sign[IO](tx, keyPair, chainId).unsafeRunSync()
        TxValidator.checkSyntacticValidity[IO](stx, chainId).attempt.unsafeRunSync().isRight shouldBe true

        val stx2 = SignedTransaction.sign[IO](tx, keyPair, ChainId(2)).unsafeRunSync()
        TxValidator.checkSyntacticValidity[IO](stx2, chainId).attempt.unsafeRunSync().isLeft shouldBe true
      }
    }
  }
}
