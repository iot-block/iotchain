package jbok.app.simulations

import java.net.InetSocketAddress

import cats.effect.IO
import cats.implicits._
import jbok.network.execution._
import jbok.JbokSpec
import jbok.network.client._
import jbok.network.rpc.{RpcClient, RpcServer}
import jbok.network.server._

class SimulationSpec extends JbokSpec {
  val impl: SimulationAPI = Simulation().unsafeRunSync()
  import jbok.network.rpc.RpcServer._
  val rpcServer                  = RpcServer().unsafeRunSync().mountAPI[SimulationAPI](impl)
  val bind                       = new InetSocketAddress("localhost", 9998)
  val server: Server[IO, String] = Server(TcpServerBuilder[IO, String], bind, rpcServer.pipe).unsafeRunSync()
  val client: Client[IO, String] = Client(TcpClientBuilder[IO, String], bind).unsafeRunSync()
  val clientApi: SimulationAPI   = RpcClient[IO](client).useAPI[SimulationAPI]
  val api                        = clientApi
  val log                        = org.log4s.getLogger
  val peerCount                  = 10
  val minerCount                 = 3

  "Simulation" should {
    "network have N node and M miner" in {
      val p = for {
        peers <- api.getNodes
        _ = peers.size shouldBe peerCount
        miners <- api.getMiners
        _ = miners.size shouldBe minerCount
      } yield ()
      p.unsafeRunSync()
    }

    "connect network should be ring" in {
      val p = for {
        peers <- api.getShakedPeerID
        _ = peers.map(_.length shouldBe 2)
      } yield ()
      p.unsafeRunSync()
    }

    "submit stx to miner" in {
      val p = for {
        peers <- api.getNodes
        _     <- api.submitStxsToNode(40, peers.last.id)
        _ = Thread.sleep(22000)
        miners <- api.getMiners
        _ = api.stopMiners(miners.map(_.id))
      } yield ()

      p.unsafeRunSync()
    }

    "submit double spend stx to miner" ignore {}
  }

  override protected def beforeAll(): Unit = {
    println("simulation start.")
    server.start.unsafeRunSync()
    Thread.sleep(2000)
    client.start.unsafeRunSync()

    val init = for {
      _ <- api.createNodesWithMiner(peerCount, minerCount)
      _ <- api.startNetwork
      _ <- api.connect("ring")
      _ = Thread.sleep(1000)
    } yield ()
    init.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    val cleanUp = for {
      blocks <- api.getBestBlock
      minBlockNumber = blocks.map(_.header.number).min
      _              = log.info(blocks.map(_.header.number).toString)
      consistent <- api.getBlocksByNumber(minBlockNumber)
      _ = consistent.tail.map(_ shouldBe consistent.head)
      _ <- api.stopNetwork
    } yield ()
    cleanUp.unsafeRunSync()

    client.stop.unsafeRunSync()
    server.stop.unsafeRunSync()
  }
}
