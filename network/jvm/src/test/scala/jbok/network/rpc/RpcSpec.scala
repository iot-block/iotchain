package jbok.network.rpc

import java.net.{InetSocketAddress, URI}
import java.util.UUID

import _root_.io.circe.syntax._
import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.network.Request
import jbok.network.client.WsClient
import jbok.network.server.WsServer

import scala.concurrent.duration._

class RpcSpec extends JbokSpec {
  trait API2 {
    def oho: IO[Int]
  }

  val impl2 = new API2 {
    override def oho: IO[Int] = IO(42)
  }

  val impl    = new TestApiImpl
  val service = RpcService().mountAPI[TestAPI[IO]](impl).mountAPI[API2](impl2)
  val bind    = new InetSocketAddress("localhost", 9002)
  val uri     = new URI("ws://localhost:9002")
  val server  = WsServer.bind(bind, service.pipe, metrics)

  "RPC Client & Server" should {
    "mount and use API" in {
      val p = for {
        client <- WsClient[IO](uri)
        api = RpcClient[IO](client).useAPI[TestAPI[IO]]
        fiber <- Stream(server.stream, Stream.sleep(1.second) ++ client.stream).parJoinUnbounded.compile.drain.start
        _ = service.handlers.size shouldBe 5
        _ = api.foo.unsafeRunSync() shouldBe impl.foo.unsafeRunSync()
        _ = api.bar.unsafeRunSync() shouldBe impl.bar.unsafeRunSync()
        _ = api.qux("oho", 42).unsafeRunSync() shouldBe impl.qux("oho", 42).unsafeRunSync()
        _ = api.error.unsafeRunSync().shouldBe(())
        _ <- fiber.cancel
      } yield ()
      p.unsafeRunSync()
    }

    "call by method name and param json" in {
      val p = for {
        ws <- WsClient[IO](uri)
        client = RpcClient[IO](ws)
        fiber <- Stream(server.stream, Stream.sleep(1.second) ++ ws.stream).parJoinUnbounded.compile.drain.start
        _ = service.handlers.size shouldBe 5
        _ <- client.request(Request.json[IO](UUID.randomUUID(), "qux", ("name", 18).asJson))
        _ <- client.request(Request.json[IO](UUID.randomUUID(), "error", ().asJson))
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
