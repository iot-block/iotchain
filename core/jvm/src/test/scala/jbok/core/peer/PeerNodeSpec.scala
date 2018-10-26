package jbok.core.peer

import jbok.JbokSpec
import jbok.crypto.signature.{ECDSA, Signature}

class PeerNodeSpec extends JbokSpec {
  "PeerNode" should {
    val keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()
    val uri     = s"jbok://${keyPair.public.bytes.toHex}@localhost:10000"
    val node    = PeerNode.fromStr(uri)

    "parse node from URI" in {
      node.isRight shouldBe true
    }

    "round trip" in {
      val node1 = node.right.get
      val uri   = node1.uri
      val node2 = PeerNode.fromUri(uri)
      node2 shouldBe node1
    }

    "handle invalid str" in {
      val uri     = s"wrong://${keyPair.public.bytes.toHex}@localhost:10000"
      PeerNode.fromStr(uri).isLeft shouldBe true
    }
  }
}
