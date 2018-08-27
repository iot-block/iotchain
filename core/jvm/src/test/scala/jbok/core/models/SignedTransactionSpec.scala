package jbok.core.models

import cats.effect.IO
import jbok.JbokSpec
import jbok.testkit.VMGens
import jbok.crypto._
import jbok.crypto.signature.ecdsa.SecP256k1
import scodec.bits.ByteVector
import scodec.bits._
import scodec.codecs._

class SignedTransactionSpec extends JbokSpec {
  "SignedTransaction" should {
    "correctly set pointSign for chainId with chain specific signing schema" in {
      forAll(VMGens.transactionGen) { tx =>
        val keyPair               = SecP256k1.generateKeyPair().unsafeRunSync()
        val chainId: Option[Byte] = Some(0x3d.toByte)
        val address               = Address(keyPair.public.bytes.kec256)
        val result                = SignedTransaction.sign(tx, keyPair, chainId)
        val senderAddress         = result.senderAddress(chainId).getOrElse(Address.empty)
        address shouldBe senderAddress
      }
    }

    "correctly set pointSign for chainId with general signing schema" in {
      forAll(VMGens.transactionGen) { tx =>
        val keyPair               = SecP256k1.generateKeyPair().unsafeRunSync()
        val chainId: Option[Byte] = Some(0x3d.toByte)
        val address               = Address(keyPair.public.bytes.kec256)
        val result                = SignedTransaction.sign(tx, keyPair, None)
        val senderAddress         = result.senderAddress(None).getOrElse(Address.empty)
        address shouldBe senderAddress
      }
    }

  }
}
