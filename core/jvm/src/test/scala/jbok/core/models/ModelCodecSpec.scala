package jbok.core.models
import cats.effect.IO
import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.testkit._
import scodec.Codec

class ModelCodecSpec extends JbokSpec {
  implicit val consensus = defaultFixture()

  def roundtrip[A: Codec](x: A) = {
    val bytes = x.encode[IO].unsafeRunSync()
    bytes.decode[IO, A].unsafeRunSync() shouldBe x
  }

  def roundtrip[A: Codec](x: A, len: Int) = {
    val bytes = x.encode[IO].unsafeRunSync()
    bytes.decode[IO, A].unsafeRunSync() shouldBe x
    bytes.length.toInt shouldBe len
  }

  "model rlp codecs" should {
    "roundtrip Address" in {
      forAll { address: Address =>
        address.bytes.length shouldBe Address.numBytes
        roundtrip(address, 20 + 1)
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
        val r       = tx.r.encode[IO].unsafeRunSync()
        val s       = tx.s.encode[IO].unsafeRunSync()
        val v       = tx.v.encode[IO].unsafeRunSync()
        val p       = tx.payload.encode[IO].unsafeRunSync()
        val nonce   = tx.nonce.encode[IO].unsafeRunSync()
        val price   = tx.gasPrice.encode[IO].unsafeRunSync()
        val limit   = tx.gasLimit.encode[IO].unsafeRunSync()
        val value   = tx.value.encode[IO].unsafeRunSync()
        val address = tx.receivingAddress.encode[IO].unsafeRunSync()
        address.length shouldBe 21
        val size     = nonce.length + price.length + limit.length + address.length + value.length + v.length + r.length + s.length + p.length
        val listSize = size + RlpCodec.listLengthCodec.encode(size).require.bytes.length
        roundtrip(tx, listSize.toInt)
      }
    }

    "roundtrip Receipt" in {
      forAll { receipt: Receipt =>
        roundtrip(receipt)
      }
    }

    "roundtrip BlockHeader" in {
      forAll { header: BlockHeader =>
        val difficulty = header.difficulty.encode[IO].unsafeRunSync()
        val number     = header.number.encode[IO].unsafeRunSync()
        val gasLimit   = header.gasLimit.encode[IO].unsafeRunSync()
        val gasUsed    = header.gasUsed.encode[IO].unsafeRunSync()
        val logBloom   = header.logsBloom.encode[IO].unsafeRunSync()
        val time       = header.unixTimestamp.encode[IO].unsafeRunSync()
        val extraData  = header.extraData.encode[IO].unsafeRunSync()
        val size       = 33 + 33 + 21 + 33 + 33 + 33 + logBloom.length + difficulty.length + number.length + gasLimit.length + gasUsed.length + time.length + extraData.length + 33 + 9
        val listSize   = size + RlpCodec.listLengthCodec.encode(size).require.bytes.length
        roundtrip(header, listSize.toInt)
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
