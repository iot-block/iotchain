package jbok.network

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.testkit.HexGen
import jbok.network.client.{Client, WebSocketClientBuilder}
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.execution._
import jbok.network.server.{Server, WebSocketServerBuilder}
import scodec.Codec
import scodec.codecs._

class WsClientServerSpec extends JbokSpec {
  case class Data(id: String, data: String)
  object Data {
    def apply(data: String): Data = {
      val uuid = UUID.randomUUID().toString
      Data(uuid, data)
    }
  }

  implicit val requestIdForData = new RequestId[Data] {
    override def id(a: Data): Option[String] = Some(a.id)
  }
  implicit val requestMethodForData: RequestMethod[Data] = new RequestMethod[Data] {
    override def method(a: Data): Option[String] = None
  }
  implicit val codecString: Codec[String] = variableSizeBytes(uint16, string(StandardCharsets.US_ASCII))
  implicit val codecData: Codec[Data]     = (codecString :: codecString).as[Data]
  val bind                                = new InetSocketAddress("localhost", 9001)
  val serverPipe: Pipe[IO, Data, Data]    = _.map { case Data(id, s) => Data(id, s"hello, $s") }
  val server: Server[IO, Data]            = Server(WebSocketServerBuilder[IO, Data], bind, serverPipe).unsafeRunSync()
  val client: Client[IO, Data]            = Client(WebSocketClientBuilder[IO, Data], bind).unsafeRunSync()

  "WebSocket Client" should {
    "write and read" in {
      forAll(HexGen.genHex(0, 2048)) { str =>
        client.write(Data(str)).unsafeRunSync()
        client.read.unsafeRunSync().data shouldBe s"hello, ${str}"
      }
    }

    "get response for request" in {
      forAll(HexGen.genHex(0, 2048)) { str =>
        val data = Data(str)
        client.request(data).unsafeRunSync().data shouldBe s"hello, ${str}"
      }
    }
  }

  "WebSocket Server" should {
    "push" ignore {
      val conns = server.connections.get.unsafeRunSync()
      conns.size shouldBe 1
      forAll(HexGen.genHex(0, 2048)) { str =>
        server.write(conns.keys.head, Data(str)).unsafeRunSync()
        client.read.unsafeRunSync().data shouldBe str
      }
    }
  }

  override protected def beforeAll(): Unit = {
    server.start.unsafeRunSync()
    Thread.sleep(3000)
    client.start.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    server.stop.unsafeRunSync()
    client.stop.unsafeRunSync()
  }
}
