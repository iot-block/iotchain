package jbok.network.transport
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import fs2._
import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import scodec.Codec
import scodec.codecs._
import scala.concurrent.duration._

class UdpTransportSpec extends JbokSpec {
  "UdpTransport" should {
    "listen" in {
      implicit val codecString: Codec[String] = variableSizeBytes(uint16, string(StandardCharsets.US_ASCII))
      val bind1                               = new InetSocketAddress("localhost", 30000)
      val bind2                               = new InetSocketAddress("localhost", 30001)

      val pipe: Pipe[IO, (InetSocketAddress, String), (InetSocketAddress, String)] = input =>
        input.map(x => {
          println(s"received ${x._2} from ${x._1}")
          x
        })

      val transport1 = UdpTransport[IO](bind1)
      val transport2 = UdpTransport[IO](bind2)
      val p = for {
        fiber1 <- transport1.serve(pipe).compile.drain.start
        fiber2 <- transport2.serve(pipe).compile.drain.start
        _      <- T.sleep(2.seconds)
        _      <- transport1.send(bind2, "oho")
        _      <- T.sleep(2.seconds)
        _      <- fiber1.cancel
        _      <- fiber2.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
