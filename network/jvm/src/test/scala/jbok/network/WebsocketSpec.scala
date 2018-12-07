package jbok.network

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.network.client.Client
import jbok.network.server.Server
import jbok.network.testkit._

class WebsocketSpec extends JbokSpec {
  val server                   = random[Server[IO]](genWsServer(9001))
  val client: Client[IO, Data] = random[Client[IO, Data]](genWsClient(9001))

  server.start.unsafeRunSync()
  Thread.sleep(1000)
  client.start.unsafeRunSync()

  "Websocket" should {
    "write and read" in {
      forAll(genHex(0, 2048)) { str =>
        client.write(Data(str)).unsafeRunSync()
        client.read.unsafeRunSync().data shouldBe str
      }
    }

    "get response for request" in {
      forAll(genHex(0, 2048)) { str =>
        val data = Data(str)
        client.request(data).unsafeRunSync().data shouldBe str
      }
    }
  }

  override protected def afterAll(): Unit = {
    client.close.unsafeRunSync()
    server.stop.unsafeRunSync()
  }
}
