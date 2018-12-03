package jbok.core.peer.discovery

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit._
import jbok.core.testkit._

class PeerTableSpec extends JbokSpec {
  "PeerTable" should {
    val table = random[PeerTable[IO]]

    "" in {
      table.loadSeedNodes()
    }
  }
}
