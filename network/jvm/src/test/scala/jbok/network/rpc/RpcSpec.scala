package jbok.network.rpc

import java.net.InetSocketAddress

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.network.client.{Client, WebSocketClientBuilder}
import jbok.network.execution._
import jbok.network.server.{Server, WebSocketServerBuilder}

class RpcSpec extends JbokSpec {
  val impl                 = new TestApiImpl
  val rpcServer: RpcServer = RpcServer().unsafeRunSync().mountAPI[TestAPI](impl)

  import RpcServer._
  val bind                                 = new InetSocketAddress("localhost", 9002)
  val serverPipe: Pipe[IO, String, String] = rpcServer.pipe
  val server: Server[IO, String]           = Server(WebSocketServerBuilder[IO, String], bind, serverPipe).unsafeRunSync()
  val client: Client[IO, String]           = Client(WebSocketClientBuilder[IO, String], bind).unsafeRunSync()
  val api: TestAPI                         = RpcClient[IO](client).useAPI[TestAPI]

  "RPC Client & Server" should {
    "mount and use API" in {
      rpcServer.handlers.size shouldBe 4
      api.foo.unsafeRunSync() shouldBe impl.foo.unsafeRunSync()
      api.bar.unsafeRunSync() shouldBe impl.bar.unsafeRunSync()
      api.qux("oho", 42).unsafeRunSync() shouldBe impl.qux("oho", 42).unsafeRunSync()
      api.error.unsafeRunSync().isLeft shouldBe true
    }

    "client subscribe" in {
      val push = Stream(0 until 10: _*).covary[IO].evalMap(i => rpcServer.notify("events", i))
      api.events.take(10).concurrently(push).compile.toList.unsafeRunSync() shouldBe (0 until 10).toList
    }
  }

  override protected def beforeAll(): Unit = {
    server.start.unsafeRunSync()
    Thread.sleep(3000)
    client.start.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    server.stop.unsafeRunSync()
    client.stop.unsafeRunSync()
  }
}
