package jbok.network

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.network.client.Client
import jbok.network.server.Server
import jbok.network.testkit._
import scala.concurrent.duration._
import fs2._

class WebsocketSpec extends JbokSpec {

  "Websocket" should {
    "write, read, and request" in {
      val server                   = random[Server[IO]](genWsServer(9001))
      val client: Client[IO, Data] = random[Client[IO, Data]](genWsClient(9001))
      val p = for {
        fiber <- Stream(server.stream, Stream.sleep[IO](1.second) ++ client.stream).parJoinUnbounded.compile.drain.start
        _ = forAll(genHex(0, 2048)) { str =>
          client.write(Data(str)).unsafeRunSync()
          client.read.unsafeRunSync().data shouldBe str
        }
        _ = forAll(genHex(0, 2048)) { str =>
          val data = Data(str)
          client.request(data).unsafeRunSync().data shouldBe str
        }
        _ <- fiber.cancel
      } yield ()
      p.unsafeRunSync()
    }
  }
}
