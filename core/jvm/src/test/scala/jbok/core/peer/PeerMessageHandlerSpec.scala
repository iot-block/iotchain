package jbok.core.peer
import jbok.core.CoreSpec
import cats.implicits._
import cats.effect.IO
import jbok.core.CoreSpec
import jbok.common.testkit._
import jbok.core.ledger.History
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.testkit._
import jbok.codec.rlp.implicits._
import jbok.core.messages.{NewBlock, SignedTransactions}
import jbok.network.Request

class PeerMessageHandlerSpec extends CoreSpec {
  "PeerMessageHandler" should {
    "broadcast NewBlockHashes to all peers and NewBlock to random part of the peers" in {
      val handler = locator.unsafeRunSync().get[PeerMessageHandler[IO]]
//      ms.syncStatus.set(SyncStatus.Done).unsafeRunSync()

      val blocks = random[List[Block]](genBlocks(1, 1))
      val peer   = random[Peer[IO]]
      val peers  = random[List[Peer[IO]]](genPeers(1, 10))
      val msg    = Request.binary[IO, NewBlock](NewBlock.name, NewBlock(blocks.head).asValidBytes)
//      handler.onNewBlockHashes(peer, )
//      val request                 = PeerRequest(peer, msg)
//      val List(newHash, newBlock) = sm.service.run(request).unsafeRunSync()
//
//      ms.onNewBlockHashes(peer,  )
//      newHash._1.run(peers).unsafeRunSync().length shouldBe peers.length
//      newBlock._1.run(peers).unsafeRunSync().length shouldBe math.min(peers.length,
//        math.max(4, math.sqrt(peers.length).toInt))
    }

    "not send block when it is known by the peer" in {
      val objects = locator.unsafeRunSync()
      val handler = objects.get[PeerMessageHandler[IO]]
//      ms.syncStatus.set(SyncStatus.Done).unsafeRunSync()

      val blocks = random[List[Block]](genBlocks(1, 1))
      val peer   = random[Peer[IO]]
      val peers  = random[List[Peer[IO]]](genPeers(1, 10))
      peers.traverse(_.markBlock(blocks.head.header.hash, blocks.head.header.number)).unsafeRunSync()

      println(handler.onNewBlock(peer, blocks.head).unsafeRunSync())

//      val msg                     = Request.binary[IO, NewBlock]("NewBlock", NewBlock(blocks.head)).unsafeRunSync()
//      val request                 = PeerRequest(peer, msg)
//      val List(newHash, newBlock) = sm.service.run(request).unsafeRunSync()
//      newHash._1.run(peers).unsafeRunSync().length shouldBe 0
//      newBlock._1.run(peers).unsafeRunSync().length shouldBe 0
    }

    "broadcast received transactions to all peers do not know yet" in {
      val objects = locator.unsafeRunSync()
      val history = objects.get[History[IO]]
      val handler = objects.get[PeerMessageHandler[IO]]

      val txs   = random[List[SignedTransaction]](genTxs(1, 10))
      val peer  = random[Peer[IO]]
      val peers = random[List[Peer[IO]]](genPeers(1, 10))
      //      val msg          = Request.binary[IO, SignedTransactions]("SignedTransactions", SignedTransactions(txs)).unsafeRunSync()
      //      val request      = PeerRequest(peer, msg)
      println(handler.onSignedTransactions(peer, SignedTransactions(txs)).unsafeRunSync())
//      val List(result) = sm.service.run(request).unsafeRunSync()
//      result._1.run(peer :: peers).unsafeRunSync() shouldBe peers
    }
  }

//"SyncManager Spec" should {
//  "broadcast NewBlockHashes to all peers and NewBlock to random part of the peers" in {
//    val sm  = locator.unsafeRunSync().get[SyncManager[IO]]
//    sm.syncStatus.set(SyncStatus.Done).unsafeRunSync()
//
//    val blocks                  = random[List[Block]](genBlocks(1, 1))
//    val peer                    = random[Peer[IO]]
//    val peers                   = random[List[Peer[IO]]](genPeers(1, 10))
//    val msg                     = Request.binary[IO, NewBlock]("NewBlock", NewBlock(blocks.head)).unsafeRunSync()
//    val request                 = PeerRequest(peer, msg)
//    val List(newHash, newBlock) = sm.service.run(request).unsafeRunSync()
//    newHash._1.run(peers).unsafeRunSync().length shouldBe peers.length
//    newBlock._1.run(peers).unsafeRunSync().length shouldBe math.min(peers.length,
//      math.max(4, math.sqrt(peers.length).toInt))
//  }
//
//  "not send block when it is known by the peer" in {
//    val objects = locator.unsafeRunSync()
//    val sm = objects.get[SyncManager[IO]]
//    sm.syncStatus.set(SyncStatus.Done).unsafeRunSync()
//
//    val blocks = random[List[Block]](genBlocks(1, 1))
//    val peer   = random[Peer[IO]]
//    val peers  = random[List[Peer[IO]]](genPeers(1, 10))
//    peers.traverse(_.markBlock(blocks.head.header.hash, blocks.head.header.number)).unsafeRunSync()
//    val msg                     = Request.binary[IO, NewBlock]("NewBlock", NewBlock(blocks.head)).unsafeRunSync()
//    val request                 = PeerRequest(peer, msg)
//    val List(newHash, newBlock) = sm.service.run(request).unsafeRunSync()
//    newHash._1.run(peers).unsafeRunSync().length shouldBe 0
//    newBlock._1.run(peers).unsafeRunSync().length shouldBe 0
//  }
//}

}
