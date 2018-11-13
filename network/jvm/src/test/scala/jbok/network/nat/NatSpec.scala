package jbok.network.nat

import java.net.{InetSocketAddress, URI}

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import jbok.common.execution._
import jbok.network.client.{Client, TcpClientBuilder}
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.server.Server

import scala.concurrent.duration._

class NatSpec extends JbokSpec {
  implicit val requestId = new RequestId[String] {
    override def id(a: String): Option[String] = None
  }
  implicit val requestMethod: RequestMethod[String] = new RequestMethod[String] {
    override def method(a: String): Option[String] = None
  }
  val pipe: Pipe[IO, String, String] = _.map(identity)

  def tcpServer(port: Int) =
    Server.tcp[IO, String](new InetSocketAddress("0.0.0.0", port), pipe).unsafeRunSync()

  def tcpClient(port: Int) =
    Client(TcpClientBuilder[IO, String], new URI(s"tcp://180.154.231.218:${port}"))

  def checkNat(natType: NatType) =
    s"NAT $natType" should {
      val internalPort = 12345
      val externalPort = 12346

      "add and delete mapping" ignore {
        val p = for {
          nat <- Nat[IO](natType)
          _   <- nat.addMapping(internalPort, externalPort, 120)
          _   <- nat.deleteMapping(externalPort)
          _   <- nat.addMapping(internalPort, externalPort, 120)
          server = tcpServer(internalPort)
          fiber  <- server.serve.compile.drain.start
          _      <- T.sleep(1.second)
          client <- tcpClient(externalPort)
          _      <- client.start
          _      <- client.write("hello")
          res    <- client.read
          _ = res shouldBe "hello"
          _ <- fiber.cancel
        } yield ()

        p.unsafeRunSync()
      }
    }

  checkNat(NatPMP)
  checkNat(NatUPnP)
}
