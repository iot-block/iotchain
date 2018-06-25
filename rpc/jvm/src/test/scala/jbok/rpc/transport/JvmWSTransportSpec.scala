package jbok.rpc.transport

import cats.effect.IO
import fs2._
import jbok.rpc.Address
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class JvmWSTransportSpec extends WordSpec with Matchers {
  "ws" should {
    import jbok.rpc.execution._
    "start and stop" in {
      val p = for {
        ws <- WSTransport[IO](Address("echo.websocket.org"))
        _ <- ws.start
        up1 <- ws.isUp
        _ <- ws.stop
        up2 <- ws.isUp
      } yield (up1, up2)

      p.map {
          case (up1, up2) =>
            up1 shouldBe true
            up2 shouldBe false
        }
        .unsafeRunSync()
    }

    "send and recv" in {
      val p = for {
        sch <- Scheduler[IO](2)
        ws <- Stream.eval(WSTransport[IO](Address("echo.websocket.org")))
        _ <- Stream.eval(ws.start)
        s <- ws.subscribe().concurrently(sch.awakeEvery[IO](1.second).evalMap(_ => ws.send("oho")))
      } yield s

      p.take(5).compile.toList.map { list =>
        list shouldBe List.fill(5)("oho")
      }.unsafeRunSync()
    }
  }
}
