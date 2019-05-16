package jbok.core.peer

import cats.effect.IO
import jbok.common.CommonSpec
import jbok.crypto.signature.{ECDSA, Signature}

class PeerUriSpec extends CommonSpec {
  "PeerUri" should {
    val keyPair = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
    val uri     = s"tcp://${keyPair.public.bytes.toHex}@localhost:10000"
    val node    = PeerUri.fromStr(uri)

    "parse from URI" in {
      node.isRight shouldBe true
    }

    "round trip" in {
      val node1 = node.right.get
      val uri   = node1.uri
      val Right(node2) = PeerUri.fromStr(uri)
      node2 shouldBe node1
    }

    "handle invalid str" in {
      val uri     = s"wrong://${keyPair.public.bytes.toHex}@localhost:10000"
      PeerUri.fromStr(uri).isLeft shouldBe true
    }
  }
}
