package jbok.core.messages

import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import jbok.core.testkit._

class MessagesCodecSpec extends JbokSpec with jbok.codec.testkit {
  "MessageCodec" should {
    "roundtrip NewBlockHashes" in {
      forAll { newBlockHashes: NewBlockHashes =>
        roundtrip(newBlockHashes)
      }
    }
  }
}
