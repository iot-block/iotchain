package jbok.network

import java.net.{InetSocketAddress, URI}
import java.nio.charset.StandardCharsets
import java.util.UUID

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit.HexGen
import jbok.network.client.{Client, WSClientBuilderPlatform}
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.server.Server
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
  val uri = new URI("ws://localhost:9001")
  val serverPipe: Pipe[IO, Data, Data]    = _.map { case Data(id, s) => Data(id, s"hello, $s") }
  val server = Server.websocket(bind, serverPipe).unsafeRunSync()
  val client: Client[IO, Data]            = Client(WSClientBuilderPlatform[IO, Data], uri).unsafeRunSync()

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

  override protected def beforeAll(): Unit = {
    server.start.unsafeRunSync()
    Thread.sleep(3000)
    client.start.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    client.stop.unsafeRunSync()
    server.stop.unsafeRunSync()
  }
}
