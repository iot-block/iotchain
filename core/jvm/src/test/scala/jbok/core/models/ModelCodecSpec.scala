package jbok.core.models

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.testkit._
import jbok.codec.testkit._
import jbok.core.CoreSpec

class ModelCodecSpec extends CoreSpec {
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
        val r        = tx.r.asValidBytes
        val s        = tx.s.asValidBytes
        val v        = tx.v.asValidBytes
        val p        = tx.payload.asValidBytes
        val nonce    = tx.nonce.asValidBytes
        val price    = tx.gasPrice.asValidBytes
        val limit    = tx.gasLimit.asValidBytes
        val value    = tx.value.asValidBytes
        val address  = tx.receivingAddress.asValidBytes
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
