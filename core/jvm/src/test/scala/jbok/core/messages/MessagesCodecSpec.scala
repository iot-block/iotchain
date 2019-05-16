package jbok.core.messages

import jbok.common.CommonSpec
import jbok.codec.rlp.implicits._
import jbok.core.testkit._
import jbok.codec.testkit._

class MessagesCodecSpec extends CommonSpec {
  "MessageCodec" should {
    "roundtrip NewBlockHashes" in {
      forAll { newBlockHashes: NewBlockHashes =>
        roundtrip(newBlockHashes)
      }
    }
  }
}
