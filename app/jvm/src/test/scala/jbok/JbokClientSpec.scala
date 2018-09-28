package jbok

import java.net.URI

import cats.effect.IO
import jbok.network.client.{Client, WebSocketClientBuilder}

import scala.io.StdIn

class JbokClientSpec extends JbokSpec {
  import jbok.network.rpc.RpcServer._

  val uri    = new URI("ws://localhost:8888")
  val client = Client(WebSocketClientBuilder[IO, String], uri).unsafeRunSync()

  println(s"rpc client connecting to ${uri}")
  client.start.unsafeRunSync()
  client.write("hi").unsafeRunSync()
  val resp = client.read.unsafeRunSync()
  println(resp)
  println(s"rpc client connected to ${uri}, press any key to stop")
  StdIn.readLine()

  override protected def afterAll(): Unit =
    client.stop.unsafeRunSync()
}
