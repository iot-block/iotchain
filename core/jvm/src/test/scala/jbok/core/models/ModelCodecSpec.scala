package jbok.core.models

import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.testkit._
import scodec.bits.ByteVector

class ModelCodecSpec extends JbokSpec with jbok.codec.testkit {
  implicit val config = testConfig

  "model rlp codecs" should {
    "roundtrip Address" in {
      forAll { address: Address =>
        address.bytes.length shouldBe Address.numBytes
        roundtripLen(address, 20 + 1)
      }
    }

    "roundtrip Account" in {
      forAll { account: Account =>
        roundtrip(account)
      }
    }

    "roundtrip Uint256" in {
      forAll { x: UInt256 =>
        roundtrip(x)
      }
    }

    "roundtrip Transaction" in {
      forAll { tx: SignedTransaction =>
        val r        = tx.r.asBytes
        val s        = tx.s.asBytes
        val v        = tx.v.asBytes
        val p        = tx.payload.asBytes
        val nonce    = tx.nonce.asBytes
        val price    = tx.gasPrice.asBytes
        val limit    = tx.gasLimit.asBytes
        val value    = tx.value.asBytes
        val address  = tx.receivingAddress.asBytes
        val size     = nonce.length + price.length + limit.length + address.length + value.length + v.length + r.length + s.length + p.length
        val listSize = size + RlpCodec.listLength.encode(size).require.bytes.length
        roundtripLen(tx, listSize.toInt)
      }
    }

    "roundtrip Receipt" in {
      forAll { receipt: Receipt =>
        roundtrip(receipt)
      }
    }

    "roundtrip BlockHeader" in {
      forAll { header: BlockHeader =>
        roundtrip(header)
      }
    }

    "roundtrip BlockBody" in {
      forAll { body: BlockBody =>
        roundtrip(body)
      }
    }

    "roundtrip Block" in {
      forAll { block: Block =>
        roundtrip(block)
      }
    }
  }
}
