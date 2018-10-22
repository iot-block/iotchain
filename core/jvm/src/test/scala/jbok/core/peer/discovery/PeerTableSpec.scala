package jbok.core.peer.discovery
import cats.effect.IO
import jbok.JbokSpec
import jbok.network.discovery.DiscoveryFixture
import jbok.persistent.KeyValueDB

class PeerTableSpec extends JbokSpec {
  val fix = new DiscoveryFixture(10000)
  "PeerTable" should {
    "return 0 node when empty" in {
      val db = KeyValueDB.inMemory[IO].unsafeRunSync()
      val table = fix.table
    }
  }
}
