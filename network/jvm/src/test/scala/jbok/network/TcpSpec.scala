package jbok.network

import cats.effect.IO
import jbok.JbokSpec
import jbok.network.client.Client
import jbok.network.server.Server
import jbok.common.testkit._
import jbok.network.testkit._
import jbok.common.execution._

class TcpSpec extends JbokSpec {
  val server = random[Server[IO]](genTcpServer(9000))
  val client = random[Client[IO, Data]](genTcpClient(9000))

  "TCP" should {
    server.start.unsafeRunSync()
    Thread.sleep(1000)
    client.start.unsafeRunSync()

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
