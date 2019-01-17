package jbok.network.client

import java.net.URI
import java.util.UUID

import cats.effect.IO
import jbok.JbokAsyncSpec
import jbok.common.execution._
import jbok.network.Request

import scala.concurrent.ExecutionContext

class WsClientSpec extends JbokAsyncSpec {

  implicit override def executionContext: ExecutionContext = EC
  "WsClient" should {
    "echo" ignore {
      val uri = new URI("ws://echo.websocket.org:80")
      for {
        client <- WsClient[IO](uri)
        _      <- client.start
        _      <- client.write(Request.withTextBody[IO](UUID.randomUUID(), "", "ohoho"))
        resp   <- client.read
        _ = println(resp)
      } yield ()
    }
  }
}
