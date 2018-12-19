package jbok.app

import java.net.InetSocketAddress

import cats.effect.IO
import jbok.app.simulations.{SimulationAPI, SimulationImpl}
import jbok.common.execution._
import jbok.common.metrics.Metrics
import jbok.network.rpc.RpcServer
import jbok.network.rpc.RpcServer._
import jbok.network.server.Server

import scala.concurrent.duration._
import scala.io.StdIn

object SimuServer {
  val bind = new InetSocketAddress("localhost", 8888)

  val impl: SimulationAPI = SimulationImpl().unsafeRunSync()
  val rpcServer           = RpcServer().unsafeRunSync().mountAPI[SimulationAPI](impl)
  val metrics             = Metrics.default[IO].unsafeRunSync()
  val server              = Server.websocket(bind, rpcServer.pipe, metrics)
  val peerCount           = 4
  val minerCount          = 1

  val init = for {
    _ <- impl.createNodesWithMiner(peerCount, minerCount)
    _ = println("connect done....")
    _ = T.sleep(5000.millis)
    _ <- impl.submitStxsToNetwork(10, "valid")
  } yield ()

  def main(args: Array[String]): Unit = {
    init.unsafeRunSync()
    val fiber = server.stream.compile.drain.start.unsafeRunSync()
    println("simulation start")

    println(s"server listen on ${bind}, press any key to quit")
    StdIn.readLine()

    println("stop simulation")

    fiber.cancel.unsafeRunSync()
  }
}
