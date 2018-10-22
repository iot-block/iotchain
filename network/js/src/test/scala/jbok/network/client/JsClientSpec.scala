package jbok.network.client

import java.net.URI
import java.nio.charset.StandardCharsets

import cats.effect.IO
import jbok.JbokAsyncSpec
import jbok.network.common.{RequestId, RequestMethod}
import org.scalatest.time.Span
import scodec.Codec
import scodec.codecs._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import jbok.common.execution._

class JsClientSpec extends JbokAsyncSpec {

  override implicit def executionContext: ExecutionContext = EC

  override def timeLimit: Span = 60.seconds
  implicit val codecString: Codec[String] = variableSizeBytes(uint16, string(StandardCharsets.US_ASCII))
  implicit val requestId = RequestId.none[String]
  implicit val requestMethod = RequestMethod.none[String]

  "JsClient" should {
    "echo" ignore {
      val uri = new URI("ws://echo.websocket.org:80")
      for {
        client <- Client(WSClientBuilderPlatform[IO, String], uri)
        _ <- client.start
        _ <- client.write("ohoho")
        resp <- client.read
        _ = println(resp)
      } yield ()
    }
  }
}
