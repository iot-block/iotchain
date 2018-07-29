package jbok.network.transport

import cats.effect.IO
import jbok.JbokSpec
import jbok.network.NetAddress
import org.scalatest.{Matchers, WordSpec}
import jbok.network.execution._

class JvmWSTransportSpec extends JbokSpec {
  "ws" should {
    "start and stop" in {
      val p = for {
        ws <- WSTransport[IO](NetAddress("echo.websocket.org"))
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
  }
}
