package jbok.core.models

import jbok.JbokSpec
import jbok.crypto._
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.core.testkit._

class SignedTransactionSpec extends JbokSpec {
  "SignedTransaction" should {
    "correctly set pointSign for chainId with chain specific signing schema" in {
      forAll { tx: Transaction =>
        val keyPair       = SecP256k1.generateKeyPair().unsafeRunSync()
        val chainId       = 0x3d.toByte
        val address       = Address(keyPair.public.bytes.kec256)
        val result        = SignedTransaction.sign(tx, keyPair, chainId)
        val senderAddress = result.senderAddress.getOrElse(Address.empty)
        address shouldBe senderAddress
      }
    }
  }
}
