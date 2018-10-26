package jbok.app

import java.net.InetSocketAddress

import cats.effect.IO
import jbok.JbokSpec
import jbok.app.api.{FilterManager, PublicAPI}
import jbok.app.api.impl.PublicApiImpl
import jbok.app.simulations.{SimulationAPI, SimulationImpl}
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.keystore.KeyStoreFixture
import jbok.core.mining.BlockMinerFixture
import jbok.network.rpc.RpcServer
import jbok.network.server.{Server, WSServerBuilder}
import jbok.common.execution._

import scala.io.StdIn

class SimuServer extends BlockMinerFixture(new CliqueFixture {}) with KeyStoreFixture with JbokSpec {
  val bind = new InetSocketAddress("localhost", 8888)

  val impl: SimulationAPI = SimulationImpl().unsafeRunSync()
  import jbok.network.rpc.RpcServer._
  val rpcServer                  = RpcServer().unsafeRunSync().mountAPI[SimulationAPI](impl)
  val server: Server[IO, String] = Server(WSServerBuilder[IO, String], bind, rpcServer.pipe).unsafeRunSync()
  val peerCount                  = 10
  val minerCount                 = 1

  val init = for {
    _ <- impl.createNodesWithMiner(peerCount, minerCount)
    _ <- impl.startNetwork
    _ <- impl.connect("ring")
    _ = Thread.sleep(1000)
    _ <- impl.submitStxsToNetwork(10, "valid")
  } yield ()
  init.unsafeRunSync()

  server.start.unsafeRunSync()
  println("simulation start")

  println(s"server listen on ${bind}, press any key to quit")
  StdIn.readLine()

  println("stop simulation")
  val cleanUp = for {
    _ <- impl.stopNetwork
  } yield ()
  cleanUp.unsafeRunSync()

  server.stop.unsafeRunSync()
}
