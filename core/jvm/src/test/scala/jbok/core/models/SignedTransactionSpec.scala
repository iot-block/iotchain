package jbok.core.models

import cats.effect.IO
import jbok.JbokSpec
import jbok.crypto.signature.SecP256k1
import jbok.testkit.VMGens

class SignedTransactionSpec extends JbokSpec {
  "SignedTransaction" should {
    "correctly set pointSign for chainId with chain specific signing schema" in {
      forAll(VMGens.transactionGen) { tx =>
        val keyPair = SecP256k1.generateKeyPair[IO].unsafeRunSync()
        val chainId: Byte = 0x3d.toByte
        val address = Address(keyPair)
        val result = SignedTransaction.sign(tx, keyPair, Some(chainId))
        address shouldBe result.senderAddress
      }
    }
  }
}
