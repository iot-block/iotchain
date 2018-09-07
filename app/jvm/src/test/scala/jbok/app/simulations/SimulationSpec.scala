package jbok.app.simulations

import java.net.InetSocketAddress

import cats.effect.IO
import jbok.JbokSpec
import jbok.network.client.{Client, WebSocketClientBuilder}
import jbok.network.rpc.{RpcClient, RpcServer}
import jbok.network.server.{Server, WebSocketServerBuilder}

class SimulationSpec extends JbokSpec {
//  val impl: SimulationAPI = ???
//  import jbok.network.rpc.RpcServer._
//  val rpcServer                  = RpcServer().unsafeRunSync().mountAPI(impl)
//  val bind                       = new InetSocketAddress("localhost", 9998)
//  val server: Server[IO, String] = Server(WebSocketServerBuilder[IO, String], bind, rpcServer.pipe).unsafeRunSync()
//  val client: Client[IO, String] = Client(WebSocketClientBuilder[IO, String], bind).unsafeRunSync()
//  val api: SimulationAPI         = RpcClient[IO](client).useAPI[SimulationAPI]
//
//  "Simulation" should {
//    "create a bunch of nodes and connect" ignore {
//      val N = 10
//      val p = for {
//        _     <- api.createNodes(N)
//        nodes <- api.getNodes
//        _ = nodes.right.get.length shouldBe N
//
//        _ <- api.startNetwork
//        _ <- api.connect("ring")
//      } yield ()
//      p.unsafeRunSync()
//    }
//  }
}
