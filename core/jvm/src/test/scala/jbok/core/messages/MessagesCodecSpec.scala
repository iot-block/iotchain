package jbok.core.messages
import cats.effect.IO
import jbok.JbokSpec
import scodec.Codec
import jbok.codec.rlp.implicits._
import jbok.core.testkit._

class MessagesCodecSpec extends JbokSpec {
  def roundtrip[A: Codec](x: A) = {
    val bytes = x.encode[IO].unsafeRunSync()
    bytes.decode[IO, A].unsafeRunSync() shouldBe x
  }

  def roundtrip[A: Codec](x: A, len: Long) = {
    val bytes = x.encode[IO].unsafeRunSync()
    bytes.decode[IO, A].unsafeRunSync() shouldBe x
    bytes.length shouldBe len
  }

  "MessageCodec" should {
    "roundtrip NewBlockHashes" in {
      forAll { newBlockHashes: NewBlockHashes =>
        roundtrip(newBlockHashes)
      }
    }
  }
}
