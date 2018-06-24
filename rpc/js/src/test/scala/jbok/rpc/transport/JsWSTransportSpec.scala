package jbok.rpc.transport

import cats.effect.IO
import fs2.Scheduler
import jbok.rpc.HostPort
import org.scalatest.{AsyncWordSpec, Matchers}
import fs2._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class JsWSTransportSpec extends AsyncWordSpec with Matchers {
  override implicit def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val sch: Scheduler = Scheduler.default

  "ws" should {
    "start and stop" in {
      val p = for {
        ws <- WSTransport[IO](HostPort("echo.websocket.org"))
        _ <- ws.start
        up1 <- ws.isUp
        _ <- ws.stop
        up2 <- ws.isUp
      } yield (up1, up2)

      p.unsafeToFuture().map {
        case (up1, up2) =>
          up1 shouldBe true
          up2 shouldBe false
      }
    }

    "send and recv" in {
      val p = for {
        ws <- Stream.eval(WSTransport[IO](HostPort("echo.websocket.org")))
        _ <- Stream.eval(ws.start)
        s <- ws.subscribe().concurrently(sch.awakeEvery[IO](1.second).evalMap(_ => ws.send("oho")))
      } yield s

      p.take(5).compile.toList.unsafeToFuture().map { list =>
        list shouldBe List.fill(5)("oho")
      }
    }
  }
}