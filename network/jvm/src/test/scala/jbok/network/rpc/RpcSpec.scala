package jbok.network.rpc

import java.net.{InetSocketAddress, URI}

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.network.client.{Client, WSClientBuilderPlatform}
import jbok.network.server.Server

class RpcSpec extends JbokSpec {
  trait API2 {
    def oho: IO[Int]
  }

  val impl2 = new API2 {
    override def oho: IO[Int] = IO(42)
  }

  val impl                 = new TestApiImpl
  val rpcServer: RpcServer = RpcServer().unsafeRunSync().mountAPI[TestAPI](impl).mountAPI[API2](impl2)

  import RpcServer._
  val bind                                 = new InetSocketAddress("localhost", 9002)
  val uri = new URI("ws://localhost:9002")
  val serverPipe: Pipe[IO, String, String] = rpcServer.pipe
  val server: Server[IO]           = Server.websocket(bind, serverPipe).unsafeRunSync()
  val client: Client[IO, String]           = Client(WSClientBuilderPlatform[IO, String], uri).unsafeRunSync()
  val api: TestAPI                         = RpcClient[IO](client).useAPI[TestAPI]

  "RPC Client & Server" should {
    "mount and use API" in {
      rpcServer.handlers.size shouldBe 5
      api.foo.unsafeRunSync() shouldBe impl.foo.unsafeRunSync()
      api.bar.unsafeRunSync() shouldBe impl.bar.unsafeRunSync()
      api.qux("oho", 42).unsafeRunSync() shouldBe impl.qux("oho", 42).unsafeRunSync()
      api.error.attempt.unsafeRunSync().isLeft shouldBe true
    }

    "client subscribe" in {
      val push = Stream(0 until 10: _*).covary[IO].evalMap[IO, Unit](i => rpcServer.notify("events", i))
      api.events.take(10).concurrently(push).compile.toList.unsafeRunSync() shouldBe (0 until 10).toList
    }
  }

  override protected def beforeAll(): Unit = {
    server.start.unsafeRunSync()
    Thread.sleep(3000)
    client.start.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    client.stop.unsafeRunSync()
    server.stop.unsafeRunSync()
  }
}
