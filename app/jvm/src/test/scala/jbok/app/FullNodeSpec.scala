package jbok.app

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.consensus.poa.clique.{Clique, CliqueConfig, CliqueConsensus}
import jbok.core.mining.TxGen
import jbok.core.pool.BlockPool
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.common.execution._
import jbok.core.History
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
    FullNode(config, consensus).unsafeRunSync()
  }
}

class FullNodeSpec extends JbokSpec {
  "FullNode" should {
    "create a full node" in new FullNodeFixture {
      val fullNodeConfig = FullNodeConfig("1", 10001)
      val fullNode       = newFullNode(fullNodeConfig)
      val p = for {
        _ <- fullNode.start
        _ <- T.sleep(1.second)
        _ <- fullNode.stop
      } yield ()

      p.unsafeRunSync()
    }

    "create a bunch of nodes and connect with ring" in new FullNodeFixture {
      val configs = FullNodeConfig.fill(10)
      val nodes   = configs.map(config => newFullNode(config))

      println(nodes.map(_.peerManager.peerNode.uri).mkString("\n"))

      val p = for {
        _ <- nodes.traverse(_.start)
        _ <- T.sleep(3.seconds)
        _ <- (nodes :+ nodes.head).sliding(2).toList.traverse[IO, Unit] {
          case a :: b :: Nil =>
            a.peerManager.addPeerNode(b.peerNode)
          case _ =>
            IO.unit
        }
        _ <- T.sleep(3.seconds)
        _ = nodes.foreach(_.peerManager.connected.unsafeRunSync().size shouldBe 2)
        _ <- nodes.traverse(_.stop)
      } yield ()

      p.unsafeRunSync()
    }

    "create a bunch of nodes and connect with star" in new FullNodeFixture {
      val N       = 10
      val configs = FullNodeConfig.fill(N)
      val nodes   = configs.map(config => newFullNode(config))

      val p = for {
        _             <- nodes.traverse(_.start)
        _             <- T.sleep(3.seconds)
        _             <- nodes.traverse(_.peerManager.addPeerNode(nodes.head.peerNode))
        _             <- T.sleep(3.seconds)
        headConnected <- nodes.head.peerManager.connected
        _ = headConnected.size shouldBe N - 1
        _ = nodes.tail.foreach(_.peerManager.connected.unsafeRunSync().size shouldBe 1)
        _ <- nodes.traverse(_.stop)
      } yield ()

      p.unsafeRunSync()
    }

    "create a bunch of nodes and connect with star and broadcast some blocks" in new FullNodeFixture {
      val N       = 4
      val configs = FullNodeConfig.fill(N)
      val nodes   = configs.map(config => newFullNode(config))

      val miner = nodes.head.miner
      val p = for {
        _ <- nodes.traverse(_.start)
        _ <- T.sleep(3.seconds)
        _ <- nodes.traverse(_.peerManager.addPeerNode(nodes.head.peerNode))
        _ <- T.sleep(3.seconds)
        _     = nodes.head.peerManager.connected.unsafeRunSync().size shouldBe N - 1
        mined = miner.miningStream.take(1).compile.toList.unsafeRunSync().head
        _ <- T.sleep(3.seconds)
        _ = nodes.head.synchronizer.history.getBestBlock.unsafeRunSync() shouldBe mined
        _ <- nodes.traverse(_.stop)
      } yield ()
      p.unsafeRunSync()
    }
  }
}
