package jbok.rpc

import java.net.InetSocketAddress

import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.JbokSpec
import jbok.rpc.Resources._
import jbok.rpc.json.{JsonrpcNotification, RequestId}
import org.scalatest.BeforeAndAfter

import scala.concurrent.duration._

class JsonrpcSpec extends JbokSpec with BeforeAndAfter {
  val addr = new InetSocketAddress("localhost", 10000)
  val server = RpcServer[IO](addr).unsafeRunSync()
  val client = RpcClient[IO](addr).unsafeRunSync()

  "rpc" should {
    "start start == start" in {
      (server.start *> server.start).unsafeRunSync() == server.start.unsafeRunSync()
      server.stop
    }

    "start server => isUp" in {
      (server.start *> server.isUp).unsafeRunSync() shouldBe true
      server.stop.unsafeRunSync()
    }

    "start stop server => !isUp" in {
      (server.start *> server.stop *> server.isUp).unsafeRunSync() shouldBe false
      server.stop.unsafeRunSync()
    }

    "start interrupt => !isUp" in {
      case object Err extends Throwable
      server.serve.zip(Stream.raiseError(Err).covary[IO]).compile.drain.attempt.unsafeRunSync() shouldBe Left(Err)

      server.isUp.unsafeRunSync() shouldBe false
    }

    "client isUp" in {
      client.isUp.unsafeRunSync() shouldBe false
      Stream(Stream.eval(server.start).drain, client.start.drain, Stream.eval(client.isUp)).joinUnbounded
        .take(1)
        .compile
        .toList
        .unsafeRunSync()
        .head shouldBe true

      client.isUp.unsafeRunSync() shouldBe false
    }

    "support websocket sse" in {
      val client = RpcClient[IO](server.addr).unsafeRunSync()
      val s = for {
        _ <- Stream.eval(server.start)
        r <- Stream(
          client.subscribe.take(10),
          client.start.drain,
          Sch.awakeEvery[IO](50.millis).map(_ => JsonrpcNotification("notify")).to(server.push).drain
        ).joinUnbounded
      } yield r

      val result = s.take(10).compile.toList.unsafeRunSync()
      result.length shouldBe 10
      server.stop.unsafeRunSync()
    }

    "support http-like req" in {
      val client = RpcClient[IO](server.addr).unsafeRunSync()
      val s = for {
        _ <- Stream.eval(server.start)
        r <- Stream(
          Stream.eval(client.call("nonexist")),
          client.start.drain
        ).joinUnbounded
      } yield r

      val result = s.take(1).compile.toList.unsafeRunSync().head
      result.id shouldBe RequestId(0)
      result.isSuccess shouldBe false
    }
  }
}
