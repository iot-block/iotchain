package jbok.network.client

import java.net.URI

import cats.effect.IO
import jbok.JbokAsyncSpec
import jbok.codec.rlp.implicits._
import jbok.common.execution._
import jbok.network.common.{RequestId, RequestMethod}

import scala.concurrent.ExecutionContext

class WsClientSpec extends JbokAsyncSpec {

  implicit override def executionContext: ExecutionContext = EC

  implicit val requestId     = RequestId.empty[String]
  implicit val requestMethod = RequestMethod.none[String]

  "WsClient" should {
    "echo" ignore {
      val uri = new URI("ws://echo.websocket.org:80")
      for {
        client <- WsClient[IO, String](uri)
        _      <- client.start
        _      <- client.write("ohoho")
        resp   <- client.read
        _ = println(resp)
      } yield ()
    }
  }

  "WsClientNode" should {
    "echo" ignore {
      val uri = new URI("ws://echo.websocket.org:80")
      for {
        client <- WsClient[IO, String](uri)
        _      <- client.start
        _      <- client.write("ohoho")
        resp   <- client.read
        _ = println(resp)
      } yield ()
    }
  }
}
