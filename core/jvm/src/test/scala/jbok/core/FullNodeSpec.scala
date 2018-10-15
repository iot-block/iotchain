package jbok.core

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.consensus.poa.clique.{Clique, CliqueConfig, CliqueConsensus}
import jbok.core.mining.TxGen
import jbok.core.pool.BlockPool
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.network.execution._
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

import scala.concurrent.duration._

class FullNodeFixture {
  val cliqueConfig  = CliqueConfig(period = 500.millis)
  val txGen         = new TxGen(3)
  val miners        = txGen.addresses.take(1).toList
  val genesisConfig = txGen.genesisConfig.copy(extraData = Clique.fillExtraData(miners.map(_.address)))

  val signer = miners.head.address
  val sign = (bv: ByteVector) => {
    SecP256k1.sign(bv.toArray, miners.head.keyPair)
  }

  def newFullNode(config: FullNodeConfig): FullNode[IO] = {
    val db      = KeyValueDB.inMemory[IO].unsafeRunSync()
    val history = History[IO](db).unsafeRunSync()
    history.loadGenesisConfig(genesisConfig).unsafeRunSync()
    val clique    = Clique[IO](cliqueConfig, history, signer, sign)
    val blockPool = BlockPool(history).unsafeRunSync()
    val consensus = new CliqueConsensus[IO](blockPool, clique)

    FullNode.apply[IO](config, history, consensus, blockPool).unsafeRunSync()
  }
}

class FullNodeSpec extends JbokSpec {
  "FullNode" should {
    "create a full node" in new FullNodeFixture {
      val fullNodeConfig = FullNodeConfig("1", 10001)
      val fullNode       = newFullNode(fullNodeConfig)
      fullNode.start.unsafeRunSync()
      fullNode.stop.unsafeRunSync()
    }

    "create a bunch of nodes and connect with ring" in new FullNodeFixture {
      val configs = FullNodeConfig.fill(10)
      val nodes   = configs.map(config => newFullNode(config))

      nodes.traverse(_.start).unsafeRunSync()
      (nodes :+ nodes.head).sliding(2).foreach {
        case a :: b :: Nil =>
          a.peerManager.connect(b.peerBindAddress).unsafeRunSync()
        case _ =>
          ()
      }

      Thread.sleep(1000)
      nodes.foreach(_.peerManager.handshakedPeers.unsafeRunSync().size shouldBe 2)
      nodes.traverse(_.stop).unsafeRunSync()
    }

    "create a bunch of nodes and connect with star" in new FullNodeFixture {
      val N       = 10
      val configs = FullNodeConfig.fill(N)
      val nodes   = configs.map(config => newFullNode(config))

      nodes.traverse(_.start).unsafeRunSync()
      nodes.tail.foreach { node =>
        node.peerManager.connect(nodes.head.peerBindAddress).unsafeRunSync()
      }
      Thread.sleep(1000)
      nodes.head.peerManager.handshakedPeers.unsafeRunSync().size shouldBe N - 1
      nodes.tail.foreach(_.peerManager.handshakedPeers.unsafeRunSync().size shouldBe 1)
      nodes.traverse(_.stop).unsafeRunSync()
    }

    "create a bunch of nodes and connect with star and broadcast some blocks" in new FullNodeFixture {
      val N       = 4
      val configs = FullNodeConfig.fill(N)
      val nodes   = configs.map(config => newFullNode(config))

      val miner = nodes.head.miner
      nodes.traverse(_.start).unsafeRunSync()
      nodes.tail.foreach { node =>
        node.peerManager.connect(nodes.head.peerBindAddress).unsafeRunSync()
      }
      Thread.sleep(1000)
      nodes.head.peerManager.handshakedPeers.unsafeRunSync().size shouldBe N - 1
      nodes.tail.foreach(_.peerManager.handshakedPeers.unsafeRunSync().size shouldBe 1)

      for (_ <- 1 to 3) {
        val mined = miner.miningStream.take(1).compile.toList.unsafeRunSync().head
        Thread.sleep(1000)
        nodes.map(_.synchronizer.history.getBestBlock.unsafeRunSync() shouldBe mined)
      }

      nodes.traverse(_.stop).unsafeRunSync()
    }
  }
}
