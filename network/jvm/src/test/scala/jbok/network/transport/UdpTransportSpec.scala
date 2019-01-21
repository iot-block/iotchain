package jbok.network.transport

import _root_.io.circe.syntax._
import java.net.InetSocketAddress
import java.util.UUID

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import jbok.network.Request
import jbok.network.testkit._

import scala.concurrent.duration._

class UdpTransportSpec extends JbokSpec {
  "UdpTransport" should {
    "listen" in {
      val bind1 = new InetSocketAddress("localhost", 30000)
      val bind2 = new InetSocketAddress("localhost", 30001)

      val (transport1, _) = UdpTransport[IO](bind1).allocated.unsafeRunSync()
      val (transport2, _) = UdpTransport[IO](bind2).allocated.unsafeRunSync()
      val p = for {
        fiber1 <- transport1.serve(echoUdpPipe).compile.drain.start
        fiber2 <- transport2.serve(echoUdpPipe).compile.drain.start
        _      <- T.sleep(1.seconds)
        _      <- transport1.send(bind2, Request.json[IO](UUID.randomUUID(), "", "oho".asJson))
        _      <- T.sleep(1.seconds)
        _      <- fiber1.cancel
        _      <- fiber2.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
