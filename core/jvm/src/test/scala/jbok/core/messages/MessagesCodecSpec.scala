package jbok.core.messages

import jbok.codec.testkit._
import jbok.core.CoreSpec

class MessagesCodecSpec extends CoreSpec {
  "MessageCodec" should {
    "roundtrip NewBlockHashes" in {
      forAll { newBlockHashes: NewBlockHashes =>
        roundtrip(newBlockHashes)
      }
    }

    "roundtrip NewBlock" in {
      forAll { newBlock: NewBlock =>
        roundtrip(newBlock)
      }
    }

    "roundtrip SignedTransactions" in {
      forAll { stxs: SignedTransactions =>
        roundtrip(stxs)
      }
    }
  }
}
