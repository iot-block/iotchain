package jbok.network.rpc

import java.net.{InetSocketAddress, URI}

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.network.client.WsClient
import jbok.network.server.Server
import jbok.codec.rlp.implicits._

import scala.concurrent.duration._

class RpcSpec extends JbokSpec {
  trait API2 {
    def oho: IO[Int]
  }

  val impl2 = new API2 {
    override def oho: IO[Int] = IO(42)
  }

  val impl                 = new TestApiImpl
  val rpcServer: RpcServer = RpcServer().unsafeRunSync().mountAPI[TestAPI[IO]](impl).mountAPI[API2](impl2)

  import RpcServer._
  val bind                                 = new InetSocketAddress("localhost", 9002)
  val uri                                  = new URI("ws://localhost:9002")
  val serverPipe: Pipe[IO, String, String] = rpcServer.pipe
  val server                               = Server.websocket(bind, serverPipe, metrics)

  "RPC Client & Server" should {
    "mount and use API" ignore {
      val p = for {
        client <- WsClient[IO, String](uri)
        api = RpcClient[IO](client).useAPI[TestAPI[IO]]
        fiber <- Stream(server.stream, Stream.sleep(1.second) ++ client.stream).parJoinUnbounded.compile.drain.start
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
