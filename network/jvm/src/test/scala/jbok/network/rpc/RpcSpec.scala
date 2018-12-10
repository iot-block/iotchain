package jbok.network.rpc

import java.net.{InetSocketAddress, URI}

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.network.client.WsClient
import jbok.network.server.Server

import scala.concurrent.duration._

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
  val uri                                  = new URI("ws://localhost:9002")
  val serverPipe: Pipe[IO, String, String] = rpcServer.pipe

  "RPC Client & Server" should {
    "mount and use API" in {
      val p = for {
        fiber  <- Server.websocket(bind, serverPipe).stream.compile.drain.start
        client <- WsClient[IO, String](uri)
        api = RpcClient[IO](client).useAPI[TestAPI]
        _ <- client.start
        _ = rpcServer.handlers.size shouldBe 5
        _ = api.foo.unsafeRunSync() shouldBe impl.foo.unsafeRunSync()
        _ = api.bar.unsafeRunSync() shouldBe impl.bar.unsafeRunSync()
        _ = api.qux("oho", 42).unsafeRunSync() shouldBe impl.qux("oho", 42).unsafeRunSync()
        _ = api.error.attempt.unsafeRunSync().isLeft shouldBe true
        _ <- fiber.cancel
      } yield ()
      p.unsafeRunSync()
    }
  }
}
