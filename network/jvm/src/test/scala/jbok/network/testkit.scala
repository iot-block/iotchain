package jbok.network

import java.net.{InetSocketAddress, URI}
import java.util.UUID

import cats.effect.IO
import fs2._
import jbok.codec.rlp.implicits._
import jbok.common.execution._
import jbok.common.testkit._
import jbok.network.client.{Client, TcpClient, WsClient}
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.server.Server
import org.scalacheck.Gen

object testkit {
  case class Data(id: String, data: String)
  object Data {
    def apply(data: String): Data = {
      val uuid = UUID.randomUUID().toString
      Data(uuid, data)
    }
    implicit val requestIdForData = new RequestId[Data] {
      override def id(a: Data): String = a.id
    }
    implicit val requestMethodForData: RequestMethod[Data] = new RequestMethod[Data] {
      override def method(a: Data): Option[String] = None
    }
  }

  val echoPipe: Pipe[IO, Data, Data] = _.map(identity)

  def genTcpServer(port: Int): Gen[Server[IO]] =
    Server.tcp[IO, Data](new InetSocketAddress(port), echoPipe)

  def genTcpClient(port: Int): Gen[Client[IO, Data]] =
    TcpClient[IO, Data](new URI(s"tcp://localhost:${port}")).unsafeRunSync()

  def genWsServer(port: Int): Gen[Server[IO]] =
    Server.websocket[IO, Data](new InetSocketAddress(port), echoPipe, metrics)

  def genWsClient(port: Int): Gen[Client[IO, Data]] =
    WsClient[IO, Data](new URI(s"tcp://localhost:${port}")).unsafeRunSync()

}
