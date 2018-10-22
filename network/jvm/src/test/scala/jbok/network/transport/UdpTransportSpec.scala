package jbok.network.transport
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import fs2._
import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import scodec.Codec
import scodec.codecs._

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

      val transport1 = UdpTransport[IO, String](bind1)
      val transport2 = UdpTransport[IO, String](bind2)
      val fiber1     = transport1.serve(pipe).compile.drain.start.unsafeRunSync()
      val fiber2     = transport2.serve(pipe).compile.drain.start.unsafeRunSync()
      Thread.sleep(2000)
      transport1.send(bind2, "oho").unsafeRunSync()
      Thread.sleep(2000)

      fiber1.cancel.unsafeRunSync()
      fiber2.cancel.unsafeRunSync()
    }
  }
}
