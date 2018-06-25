package jbok.rpc

import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.rpc.execution._
import jbok.rpc.server.Server
import jbok.rpc.transport.WSTransport
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}

class JvmServerSpec extends WordSpec with BeforeAndAfter with Matchers {
  val addr = Address("localhost", 10001)
  val api = TestAPI.apiImpl
  val service = JsonRPCService[IO].mountAPI(api)
  val server = Server[IO](addr, service.handle).unsafeRunSync()
  val client = WSTransport[IO](server.addr).unsafeRunSync()

  "rpc" should {
    "start => isUp" in {
      (server.start *> server.isUp).unsafeRunSync() shouldBe true
      server.stop.unsafeRunSync()
    }

    "start stop => !isUp" in {
      (server.start *> server.stop *> server.isUp).unsafeRunSync() shouldBe false
      server.stop.unsafeRunSync()
    }

    "support sse" in {
      val s = for {
        _ <- Stream.eval(server.start)
        _ <- Stream.eval(client.start)
        r <- client
          .subscribe()
          .take(10)
          .concurrently(Stream("notify").repeat.take(10).covary[IO].to(server.push))
      } yield r

      val result = s.compile.toList.unsafeRunSync()
      result.length shouldBe 10
      client.stop.unsafeRunSync()
      server.stop.unsafeRunSync()
    }
  }
}
