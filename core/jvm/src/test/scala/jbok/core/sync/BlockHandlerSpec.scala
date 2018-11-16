package jbok.core.sync

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.models.Block
import jbok.core.peer.PeerSet
import jbok.common.testkit._
import jbok.core.testkit._
import cats.implicits._

class BlockHandlerSpec extends JbokSpec {
  implicit val consensus = defaultFixture()

  "BlockHandler" should {
    "random block handler should have same genesis" in {
      val handler1 = random[BlockHandler[IO]]
      val handler2 = random[BlockHandler[IO]]
      val genesis1 = handler1.executor.history.genesisHeader.unsafeRunSync()
      val genesis2 = handler2.executor.history.genesisHeader.unsafeRunSync()
      genesis1 shouldBe genesis2
      handler1.executor.history.db shouldNot be(handler2.executor.history.db)
    }

    "broadcast NewBlockHashes to all peers and NewBlock to random part of the peers" in {
      val blockHandler = random[BlockHandler[IO]]
      val blocks       = random[List[Block]](genBlocks(1, 1))
      val peerSet      = random[PeerSet[IO]](genPeerSet(1, 10))
      val peerLength   = peerSet.connected.unsafeRunSync().length
      val result       = blockHandler.handleBlock(blocks.head, peerSet).unsafeRunSync()

      result.length shouldBe (peerLength + math.min(peerLength, math.max(4, math.sqrt(peerLength).toInt)))
    }

    "not send block when it is known by the peer" in {
      val blockHandler = random[BlockHandler[IO]]
      val blocks       = random[List[Block]](genBlocks(1, 1))
      val peerSet      = random[PeerSet[IO]](genPeerSet(1, 10))
      peerSet.connected.flatMap(_.traverse(_.markBlock(blocks.head.header.hash))).unsafeRunSync()
      val result = blockHandler.handleBlock(blocks.head, peerSet).unsafeRunSync()
      result.length shouldBe 0
    }
  }
}
