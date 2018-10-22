package jbok.core.peer
import cats.effect.IO
import jbok.JbokSpec
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.persistent.KeyValueDB

class PeerNodeManagerSpec extends JbokSpec {
  "PeerNodeManager" should {
    val db = KeyValueDB.inMemory[IO].unsafeRunSync()
    val nodeManager = PeerNodeManager[IO](db).unsafeRunSync()

    "add uri" in {
      val keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()
      val node = PeerNode(keyPair.public, "localhost", 10000)
      nodeManager.add(node.uri).unsafeRunSync()
      nodeManager.getAll.unsafeRunSync().head shouldBe node
    }
  }
}
