package jbok.core.sync

import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.config.Configs.{MiningConfig, SyncConfig}
import jbok.core.messages._
import jbok.core.mining.BlockMiner
import jbok.core.models._
import jbok.core.peer._
import jbok.core.testkit._
import jbok.crypto.authds.mpt.MptNode
import jbok.crypto.testkit._
import scodec.bits.ByteVector

import scala.concurrent.duration._

class SyncManagerSpec extends JbokSpec {

  implicit val fixture = defaultFixture()

  "SyncManager Request Service" should {
    "return receipts by block hashes" in {
      val handler = random[SyncManager[IO]]
      forAll { (peer: Peer[IO], ps: PeerSet[IO], receipts: List[Receipt], hash: ByteVector) =>
        handler.history.putReceipts(hash, receipts).unsafeRunSync()
        val msg = GetReceipts(hash :: Nil)
        val req = Request(peer, ps, msg)
        val res = handler.service.run(req).unsafeRunSync()
        res shouldBe peer -> Receipts(receipts :: Nil, msg.id) :: Nil
      }
    }

    "return BlockBodies by block hashes" in {
      val handler = random[SyncManager[IO]]
      forAll { (peer: Peer[IO], ps: PeerSet[IO], block: Block) =>
        handler.history.putBlockBody(block.header.hash, block.body).unsafeRunSync()
        val msg     = GetBlockBodies(block.header.hash :: Nil)
        val request = Request(peer, ps, msg)
        val res     = handler.service.run(request).unsafeRunSync()
        res shouldBe peer -> BlockBodies(block.body :: Nil, msg.id) :: Nil
      }
    }

    "return BlockHeaders by block number/hash" in {
      val handler = random[SyncManager[IO]]

      val baseHeader: BlockHeader   = BlockHeader.empty
      val firstHeader: BlockHeader  = baseHeader.copy(number = 3)
      val secondHeader: BlockHeader = baseHeader.copy(number = 4)

      handler.history.putBlockHeader(firstHeader).unsafeRunSync()
      handler.history.putBlockHeader(secondHeader).unsafeRunSync()
      handler.history.putBlockHeader(baseHeader.copy(number = 5)).unsafeRunSync()
      handler.history.putBlockHeader(baseHeader.copy(number = 6)).unsafeRunSync()
      forAll { (peer: Peer[IO], ps: PeerSet[IO]) =>
        val msg1    = GetBlockHeaders(Left(3), 2, 0, reverse = false)
        val request = Request(peer, ps, msg1)
        val res     = handler.service.run(request).unsafeRunSync()
        res shouldBe peer -> BlockHeaders(firstHeader :: secondHeader :: Nil, msg1.id) :: Nil
        val msg2     = GetBlockHeaders(Right(firstHeader.hash), 2, 0, reverse = false)
        val request2 = Request(peer, ps, msg2)
        val res2     = handler.service.run(request2).unsafeRunSync()
        res2 shouldBe peer -> BlockHeaders(firstHeader :: secondHeader :: Nil, msg2.id) :: Nil
      }
    }

    "return NodeData by node hashes" in {
      val handler = random[SyncManager[IO]]
      forAll { (peer: Peer[IO], ps: PeerSet[IO], node: MptNode) =>
        val msg = GetNodeData(NodeHash.StateMptNodeHash(node.hash) :: Nil)
        handler.history.putMptNode(node.hash, node.bytes).unsafeRunSync()
        val request = Request(peer, ps, msg)
        val res     = handler.service.run(request).unsafeRunSync()
        res shouldBe peer -> NodeData(node.bytes :: Nil, msg.id) :: Nil
      }
    }
  }

  "SyncManager Block Service" should {
    "broadcast NewBlockHashes to all peers and NewBlock to random part of the peers" in {
      val sm         = random[SyncManager[IO]]
      val blocks     = random[List[Block]](genBlocks(1, 1))
      val peer       = random[Peer[IO]]
      val peerSet    = random[PeerSet[IO]](genPeerSet(1, 10))
      val peerLength = peerSet.connected.unsafeRunSync().length
      val request    = Request(peer, peerSet, NewBlock(blocks.head))
      val result     = sm.service.run(request).unsafeRunSync()
      result.length shouldBe (peerLength + math.min(peerLength, math.max(4, math.sqrt(peerLength).toInt)))
    }

    "not send block when it is known by the peer" in {
      val sm      = random[SyncManager[IO]]
      val blocks  = random[List[Block]](genBlocks(1, 1))
      val peer    = random[Peer[IO]]
      val peerSet = random[PeerSet[IO]](genPeerSet(1, 10))
      peerSet.connected.flatMap(_.traverse(_.markBlock(blocks.head.header.hash))).unsafeRunSync()
      val request = Request(peer, peerSet, NewBlock(blocks.head))
      val result  = sm.service.run(request).unsafeRunSync()
      result.length shouldBe 0
    }
  }

  "SyncManager Transaction Service" should {
    "broadcast received transactions to all peers do not know yet" in {
      val sm = random[SyncManager[IO]]
      val txs = random[List[SignedTransaction]](genTxs(1, 10))
      val peer       = random[Peer[IO]]
      val peerSet    = random[PeerSet[IO]](genPeerSet(1, 10))
      val request    = Request(peer, peerSet, SignedTransactions(txs))
      val result = sm.service.run(request).unsafeRunSync()
      result.length shouldBe peerSet.connected.unsafeRunSync().length
    }
  }

  "SyncManager FullSync" should {
    "sync to the highest state" in {
      val sm1   = random[SyncManager[IO]](genSyncManager()(fixture))
      val sm2   = random[SyncManager[IO]](genSyncManager()(fixture.copy(port = fixture.port + 1)))
      val sm3   = random[SyncManager[IO]](genSyncManager()(fixture.copy(port = fixture.port + 2)))
      val miner = BlockMiner[IO](MiningConfig(), sm1.executor)

      // let miner mine 10 blocks first
      val blocks = miner.stream.take(10).compile.toList.unsafeRunSync()
      println(sm1.history.getBestBlockNumber.unsafeRunSync())

      val p = for {
        fiber <- Stream(sm1, sm2, sm3).map(_.peerManager.start).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- sm1.serve.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- sm2.peerManager.addPeerNode(sm1.peerManager.peerNode)
        _     <- sm3.peerManager.addPeerNode(sm1.peerManager.peerNode)
        _     <- T.sleep(1.second)

        _     <- sm2.fullSync.stream.unNone.take(1).compile.drain
        _     <- sm3.fullSync.stream.unNone.take(1).compile.drain
        best1 <- sm1.history.getBestBlock
        best2 <- sm2.history.getBestBlock
        best3 <- sm2.history.getBestBlock
        _ = best1 shouldBe blocks.last.block
        _ = best1 shouldBe best2
        _ = best1 shouldBe best3
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }

  "SyncManager FastSync" should {
    val config = SyncConfig(targetBlockOffset = 0)

    "sync to the median state" in {
      val sm1 = random[SyncManager[IO]](genSyncManager(config)(fixture))
      val sm2 = random[SyncManager[IO]](genSyncManager(config)(fixture.copy(port = fixture.port + 1)))
      val sm3 = random[SyncManager[IO]](genSyncManager(config)(fixture.copy(port = fixture.port + 2)))

      val N      = 10
      val blocks = random[List[Block]](genBlocks(N, N))
      sm1.executor.importBlocks(blocks).unsafeRunSync()
      sm2.executor.importBlocks(blocks).unsafeRunSync()

      val p = for {
        fiber <- Stream(sm1, sm2, sm3).map(_.peerManager.start).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- Stream(sm1, sm2).map(_.serve).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- sm3.peerManager.addPeerNode(sm1.peerManager.peerNode)
        _     <- sm3.peerManager.addPeerNode(sm2.peerManager.peerNode)
        _     <- T.sleep(1.second)

        _ <- sm3.fastSync.stream.compile.drain
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
