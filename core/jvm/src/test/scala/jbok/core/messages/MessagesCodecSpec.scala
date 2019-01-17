package jbok.core.messages

import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import jbok.core.testkit._
import jbok.codec.testkit._

class MessagesCodecSpec extends JbokSpec {
  "MessageCodec" should {
    "roundtrip NewBlockHashes" in {
      forAll { newBlockHashes: NewBlockHashes =>
        roundtrip(newBlockHashes)
      }
    }
  }
}
