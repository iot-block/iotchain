package jbok.crypto.hash

import jbok.JbokSpec
import jbok.crypto._
import scodec.bits._

class HashSpec extends JbokSpec {
  val x = ByteVector("jbok".getBytes())
  "hash" should {
    "impl keccak256" in {
      x.kec256 shouldBe hex"ec2efe5a15b243d8e1de351847914f166c4ac52c1ecbebe6743503360511f728"
    }

    "impl keccak512" in {
      x.kec512 shouldBe hex"5cc1aba4bd66bfdc928229ac41957ab8255afaf8ae1cf074eb5d4d0d2080092981e91049051c97e11683bb6aa84abfb2e1caf95938af41b102a96f391390756f"
    }

    "impl sha256" in {
      x.sha256 shouldBe hex"b6d2a9f078b043dd79ab2f19a591b3e3a62de67aa80da004ac2a6ddfb0bb0964"
    }

    "impl ripemd160" in {
      x.ripemd160 shouldBe hex"8f463e54903130beacfccc0a3c9d3754d42978b6"
    }
  }
}
