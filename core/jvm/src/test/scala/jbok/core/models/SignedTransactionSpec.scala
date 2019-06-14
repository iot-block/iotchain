package jbok.core.models

import cats.effect.IO
import io.circe.CursorOp.DownField
import io.circe.DecodingFailure
import jbok.core.CoreSpec
import jbok.core.validators.TxValidator
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, Signature}
import io.circe.parser._

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

    "decode huge json object" in {
      val left  = "{"
      val key   = "\"key\""
      val value = "100"
      val right = "}"

      def genJson(value: String, n: Int): String = {
        val obj = left + key + ":" + value + right
        if (n > 0) {
          genJson(obj, n - 1)
        } else {
          obj
        }
      }
      val json = genJson(value, 5000)
      parse(json)

      val error = decode[SignedTransaction](json)
      error shouldBe Left(DecodingFailure("Attempt to decode value on failed cursor", List(DownField("nonce"))))
    }
  }
}
