package jbok.core.models

import cats.effect.IO
import jbok.JbokSpec
import jbok.crypto._
import jbok.core.testkit._
import jbok.crypto.signature.{ECDSA, Signature}

class SignedTransactionSpec extends JbokSpec {
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
  }
}
