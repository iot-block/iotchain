package jbok.core.sync

import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.defaults.reference
import jbok.core.ledger.TypedBlock.SyncBlocks
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
  implicit val config = testConfig

  val List(config1, config2, config3) = FullNodeConfig.fill(config, 3)

  "SyncManager Request Service" should {
    "return receipts by block hashes" in {
      val handler = random[SyncManager[IO]]
      forAll { (peer: Peer[IO], receipts: List[Receipt], hash: ByteVector) =>
        handler.history.putReceipts(hash, receipts).unsafeRunSync()
        val msg       = GetReceipts(hash :: Nil)
        val req       = Request(peer, msg)
        val List(res) = handler.service.run(req).unsafeRunSync()
        res._2 shouldBe Receipts(receipts :: Nil, msg.id)
      }
    }

    "return BlockBodies by block hashes" in {
      val handler = random[SyncManager[IO]]
      forAll { (peer: Peer[IO], block: Block) =>
        handler.history.putBlockBody(block.header.hash, block.body).unsafeRunSync()
        val msg       = GetBlockBodies(block.header.hash :: Nil)
        val request   = Request(peer, msg)
        val List(res) = handler.service.run(request).unsafeRunSync()
        res._2 shouldBe BlockBodies(block.body :: Nil, msg.id)
      }
    }

    "return BlockHeaders by block number/hash" in {
      val handler = random[SyncManager[IO]]

      val baseHeader: BlockHeader   = random[BlockHeader]
      val firstHeader: BlockHeader  = baseHeader.copy(number = 3)
      val secondHeader: BlockHeader = baseHeader.copy(number = 4)

      handler.history.putBlockHeader(firstHeader).unsafeRunSync()
      handler.history.putBlockHeader(secondHeader).unsafeRunSync()
      handler.history.putBlockHeader(baseHeader.copy(number = 5)).unsafeRunSync()
      handler.history.putBlockHeader(baseHeader.copy(number = 6)).unsafeRunSync()
      forAll { peer: Peer[IO] =>
        val msg1      = GetBlockHeaders(Left(3), 2, 0, reverse = false)
        val request   = Request(peer, msg1)
        val List(res) = handler.service.run(request).unsafeRunSync()
        res._2 shouldBe BlockHeaders(firstHeader :: secondHeader :: Nil, msg1.id)
        val msg2       = GetBlockHeaders(Right(firstHeader.hash), 2, 0, reverse = false)
        val request2   = Request(peer, msg2)
        val List(res2) = handler.service.run(request2).unsafeRunSync()
        res2._2 shouldBe BlockHeaders(firstHeader :: secondHeader :: Nil, msg2.id)
      }
    }

    "return NodeData by node hashes" in {
      val handler = random[SyncManager[IO]]
      forAll { (peer: Peer[IO], node: MptNode) =>
        val msg = GetNodeData(NodeHash.StateMptNodeHash(node.hash) :: Nil)
        handler.history.putMptNode(node.hash, node.bytes).unsafeRunSync()
        val request   = Request(peer, msg)
        val List(res) = handler.service.run(request).unsafeRunSync()
        res._2 shouldBe NodeData(node.bytes :: Nil, msg.id)
      }
    }
  }

  "SyncManager Block Service" should {
    "broadcast NewBlockHashes to all peers and NewBlock to random part of the peers" in {
      val sm                      = random[SyncManager[IO]](genSyncManager(SyncStatus.SyncDone))
      val blocks                  = random[List[Block]](genBlocks(1, 1))
      val peer                    = random[Peer[IO]]
      val peers                   = random[List[Peer[IO]]](genPeers(1, 10))
      val request                 = Request(peer, NewBlock(blocks.head))
      val List(newHash, newBlock) = sm.service.run(request).unsafeRunSync()
      newHash._1.run(peers).unsafeRunSync().length shouldBe peers.length
      newBlock._1.run(peers).unsafeRunSync().length shouldBe math.min(peers.length,
                                                                      math.max(4, math.sqrt(peers.length).toInt))
    }

    "not send block when it is known by the peer" in {
      val sm     = random[SyncManager[IO]](genSyncManager(SyncStatus.SyncDone))
      val blocks = random[List[Block]](genBlocks(1, 1))
      val peer   = random[Peer[IO]]
      val peers  = random[List[Peer[IO]]](genPeers(1, 10))
      peers.traverse(_.markBlock(blocks.head.header.hash)).unsafeRunSync()
      val request                 = Request(peer, NewBlock(blocks.head))
      val List(newHash, newBlock) = sm.service.run(request).unsafeRunSync()
      newHash._1.run(peers).unsafeRunSync().length shouldBe 0
      newBlock._1.run(peers).unsafeRunSync().length shouldBe 0
    }
  }

  "SyncManager Transaction Service" should {
    "broadcast received transactions to all peers do not know yet" in {
      val sm           = random[SyncManager[IO]]
      val txs          = random[List[SignedTransaction]](genTxs(1, 10))
      val peer         = random[Peer[IO]]
      val peers        = random[List[Peer[IO]]](genPeers(1, 10))
      val request      = Request(peer, SignedTransactions(txs))
      val List(result) = sm.service.run(request).unsafeRunSync()
      result._1.run(peer :: peers).unsafeRunSync() shouldBe peers
    }
  }

  "SyncManager FullSync" should {
    "sync to the highest state" in {
      val sm1   = random[SyncManager[IO]](genSyncManager()(config1))
      val sm2   = random[SyncManager[IO]](genSyncManager()(config2))
      val sm3   = random[SyncManager[IO]](genSyncManager()(config3))
      val miner = BlockMiner[IO](reference.mining, sm1).unsafeRunSync()

      // let miner mine 10 blocks first
      val blocks = miner.stream.take(10).compile.toList.unsafeRunSync()
      println(sm1.history.getBestBlockNumber.unsafeRunSync())

      val p = for {
        fiber <- Stream(sm1, sm2, sm3).map(_.peerManager.stream).parJoinUnbounded.compile.drain.start
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
        best3 <- sm3.history.getBestBlock
        _ = best1 shouldBe blocks.last.block
        _ = best1 shouldBe best2
        _ = best1 shouldBe best3
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }

  "SyncManager FastSync" should {

    "fast sync to median state if best peer number - offset >= current best number" in {
      val List(config1, config2, config3) =
        FullNodeConfig.fill(config.withSync(_.copy(fastEnabled = true, fastSyncOffset = 0)), 3)
      val sm1 = random[SyncManager[IO]](genSyncManager()(config1))
      val sm2 = random[SyncManager[IO]](genSyncManager()(config2))
      val sm3 = random[SyncManager[IO]](genSyncManager()(config3))

      val N      = 10
      val blocks = random[List[Block]](genBlocks(N, N))
      sm1.executor.handleSyncBlocks(SyncBlocks(blocks, None)).unsafeRunSync()
      sm2.executor.handleSyncBlocks(SyncBlocks(blocks, None)).unsafeRunSync()

      val p = for {
        fiber <- Stream(sm1, sm2, sm3).map(_.peerManager.stream).parJoinUnbounded.compile.drain.start
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

    "skip fast sync if best peer number - offset < current best number" in {
      val List(config1, config2, config3) =
        FullNodeConfig.fill(config.withSync(_.copy(fastEnabled = true)), 3)
      val sm1  = random[SyncManager[IO]](genSyncManager()(config1))
      val sm2  = random[SyncManager[IO]](genSyncManager()(config2))
      val sm3  = random[SyncManager[IO]](genSyncManager()(config3))

      val N      = 10
      val blocks = random[List[Block]](genBlocks(N, N))
      sm1.executor.handleSyncBlocks(SyncBlocks(blocks, None)).unsafeRunSync()
      sm2.executor.handleSyncBlocks(SyncBlocks(blocks, None)).unsafeRunSync()

      val p = for {
        fiber <- Stream(sm1, sm2, sm3).map(_.peerManager.stream).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- Stream(sm1, sm2).map(_.serve).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- sm3.peerManager.addPeerNode(sm1.peerManager.peerNode)
        _     <- sm3.peerManager.addPeerNode(sm2.peerManager.peerNode)
        _     <- T.sleep(1.second)

        _     <- sm3.fastSync.stream.compile.drain
        best3 <- sm3.history.getBestBlock
        _ = best3.header.number shouldBe 0
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }

  "SyncManager" should {
    "start with FastSync then switch to FullSync" in {
      val List(config1, config2, confgi3) = FullNodeConfig.fill(
        config.withSync(_.copy(fastEnabled = true, fastSyncOffset = 5)),
        3
      )
      val sm1     = random[SyncManager[IO]](genSyncManager()(config1))
      val sm2     = random[SyncManager[IO]](genSyncManager()(config2))
      val sm3     = random[SyncManager[IO]](genSyncManager()(config3))

      val N      = 10
      val blocks = random[List[Block]](genBlocks(N, N))
      sm1.executor.handleSyncBlocks(SyncBlocks(blocks, None)).unsafeRunSync()
      sm2.executor.handleSyncBlocks(SyncBlocks(blocks, None)).unsafeRunSync()

      val p = for {
        fiber <- Stream(sm1, sm2, sm3).map(_.peerManager.stream).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- Stream(sm1, sm2).map(_.serve).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- sm3.peerManager.addPeerNode(sm1.peerManager.peerNode)
        _     <- sm3.peerManager.addPeerNode(sm2.peerManager.peerNode)
        _     <- T.sleep(1.second)

        _     <- sm3.sync.unNone.take(1).compile.drain
        best1 <- sm1.history.getBestBlock
        best3 <- sm3.history.getBestBlock
        _ = best3 shouldBe best1
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
