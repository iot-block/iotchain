package jbok.network

import java.net.{InetSocketAddress, URI}

import cats.effect.IO
import fs2._
import jbok.common.execution._
import jbok.common.testkit._
import jbok.network.client.{Client, TcpClient, WsClient}
import jbok.network.server.{Server, TcpServer, WsServer}
import org.scalacheck.Gen

object testkit {
  val log = jbok.common.log.getLogger("testkit")
  val echoPipe: Pipe[IO, Message[IO], Message[IO]] =
    _.evalMap[IO, Option[Message[IO]]] {
      case req: Request[IO] =>
        IO.pure(Some(Response(req.id, contentType = req.contentType, body = req.body)))
      case res: Response[IO] =>
        IO.pure(None)
    }.unNone

  val echoUdpPipe: Pipe[IO, (InetSocketAddress, Message[IO]), (InetSocketAddress, Message[IO])] =
    _.evalMap[IO, Option[(InetSocketAddress, Message[IO])]] {
      case (remote, req: Request[IO]) =>
        IO.pure(Some(remote -> Response(req.id, contentType = req.contentType, body = req.body)))
      case (remote, res: Response[IO]) => IO.pure(None)
    }.unNone

  def genTcpServer(port: Int): Gen[Server[IO]] =
    TcpServer.bind[IO](new InetSocketAddress(port), echoPipe)

  def genTcpClient(port: Int): Gen[Client[IO]] =
    TcpClient[IO](new URI(s"tcp://localhost:${port}")).unsafeRunSync()

  def genWsServer(port: Int): Gen[Server[IO]] =
    WsServer.bind[IO](new InetSocketAddress(port), echoPipe, metrics)

  def genWsClient(port: Int): Gen[Client[IO]] =
    WsClient[IO](new URI(s"tcp://localhost:${port}")).unsafeRunSync()

}
