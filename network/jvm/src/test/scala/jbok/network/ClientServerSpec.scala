package jbok.network

import java.util.UUID

import _root_.io.circe.syntax._
import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.network.client.Client
import jbok.network.server.Server
import jbok.network.testkit._

import scala.concurrent.duration._
import fs2._

class ClientServerSpec extends JbokSpec {
  def check(client: Client[IO], server: Server[IO]) =
    "write, read and request" in {
      val p = for {
        fiber <- Stream(server.stream, Stream.sleep[IO](1.second) ++ client.stream).parJoinUnbounded.compile.drain.start
        _ = forAll(genHex(1024 * 1500, 1024 * 1500)) { str =>
          val req = Request.json[IO](UUID.randomUUID(), "", str.asJson)
          client.write(req).unsafeRunSync()
          val res = client.read.unsafeRunSync()
          res.bodyAsJson.unsafeRunSync() shouldBe str.asJson
        }
        _ = forAll(genHex(1024 * 1500, 1024 * 1500)) { str =>
          val req = Request.json[IO](UUID.randomUUID(), "", str.asJson)
          client.request(req).unsafeRunSync().bodyAsJson.unsafeRunSync() shouldBe str.asJson
        }
        _ <- fiber.cancel
      } yield ()
      p.unsafeRunSync()
    }

  "tcp" should {
    val tcpClient = random[Client[IO]](genTcpClient(29000))
    val tcpServer = random[Server[IO]](genTcpServer(29000))
    check(tcpClient, tcpServer)
  }

  "websocket" should {
    val wsClient = random[Client[IO]](genWsClient(29001))
    val wsServer = random[Server[IO]](genWsServer(29001))
    check(wsClient, wsServer)
  }
}
