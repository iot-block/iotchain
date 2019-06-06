package jbok.core.models

import jbok.codec.rlp.RlpCodecHelper
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
      forAll { tx: Transaction =>
        roundtrip(tx)
      }
    }

    "roundtrip SignedTransaction" in {
      forAll { tx: SignedTransaction =>
        val r       = tx.r.encoded
        val s       = tx.s.encoded
        val v       = tx.v.encoded
        val p       = tx.payload.encoded
        val nonce   = tx.nonce.encoded
        val price   = tx.gasPrice.encoded
        val limit   = tx.gasLimit.encoded
        val value   = tx.value.encoded
        val address = tx.receivingAddress.encoded
        val size = List(r, s, v, p, nonce, price, limit, value, address)
          .foldLeft(0L)(_ + _.bytes.length)
        val listSize = size + RlpCodecHelper.listLengthCodec.encode(size).require.bytes.length
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
