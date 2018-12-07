package jbok.network.transport
import java.net.InetSocketAddress

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import jbok.common.execution._

import scala.concurrent.duration._

class UdpTransportSpec extends JbokSpec {
  "UdpTransport" should {
    "listen" in {
      val bind1 = new InetSocketAddress("localhost", 30000)
      val bind2 = new InetSocketAddress("localhost", 30001)

      val pipe: Pipe[IO, (InetSocketAddress, String), (InetSocketAddress, String)] = input =>
        input.map(x => {
          x
        })

      val (transport1, _) = UdpTransport[IO](bind1).allocated.unsafeRunSync()
      val (transport2, _) = UdpTransport[IO](bind2).allocated.unsafeRunSync()
      val p = for {
        fiber1 <- transport1.serve(pipe).compile.drain.start
        fiber2 <- transport2.serve(pipe).compile.drain.start
        _      <- T.sleep(1.seconds)
        _      <- transport1.send(bind2, "oho")
        _      <- T.sleep(1.seconds)
        _      <- fiber1.cancel
        _      <- fiber2.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
