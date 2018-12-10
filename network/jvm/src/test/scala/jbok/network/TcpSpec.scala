package jbok.network

import cats.effect.IO
import jbok.JbokSpec
import jbok.network.client.Client
import jbok.network.server.Server
import jbok.common.testkit._
import jbok.network.testkit._
import jbok.common.execution._
import scala.concurrent.duration._

class TcpSpec extends JbokSpec {
  "TCP" should {
    "write, read and request" in {
      val server = random[Server[IO]](genTcpServer(9000))
      val client = random[Client[IO, Data]](genTcpClient(9000))
      val p = for {
        fiber <- server.stream.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- client.start
        _ = forAll(genHex(0, 2048)) { str =>
          client.write(Data(str)).unsafeRunSync()
          client.read.unsafeRunSync().data shouldBe str
        }
        _ = forAll(genHex(0, 2048)) { str =>
          val data = Data(str)
          client.request(data).unsafeRunSync().data shouldBe str
        }
        _ <- client.close
        _ <- fiber.cancel
      } yield ()
      p.unsafeRunSync()
    }
  }
}
