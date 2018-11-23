package jbok.app

import java.net.URI

import cats.effect.IO
import fs2._
import jbok.app.api.{PrivateAPI, PublicAPI}
import jbok.common.execution._
import jbok.network.client.{Client, WsClient}
import jbok.network.rpc.RpcClient

import scala.concurrent.duration._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("JbokClient")
@JSExportAll
case class JbokClient(uri: URI, client: Client[IO, String], admin: PrivateAPI, public: PublicAPI) {
  def status: Stream[IO, Boolean] =
    for {
      _    <- Stream.awakeEvery[IO](5.seconds)
      isUp <- Stream.eval(client.haltWhenTrue.get.map(!_))
    } yield isUp
}

@JSExportTopLevel("JbokClientObject")
@JSExportAll
object JbokClient {
  import jbok.network.rpc.RpcServer._
  def apply(uri: URI): IO[JbokClient] =
    for {
      client <- WsClient[IO, String](uri)
      admin  = RpcClient(client).useAPI[PrivateAPI]
      public = RpcClient(client).useAPI[PublicAPI]
      _ <- client.start
    } yield JbokClient(uri, client, admin, public)

//  def http(ip: String): IO[JbokClient] = {
//    val uri = new URI(ip)
//    for {
//      client <- Client(TcpClientBuilder[IO, String], uri)
//      admin  = RpcClient(client).useAPI[PrivateAPI]
//      public = RpcClient(client).useAPI[PublicAPI]
//      _ <- client.start
//    } yield JbokClient(uri, client, admin, public)
//  }

  def webSocket(url: String): IO[JbokClient] = {
    val uri = new URI(url)
    apply(uri)
  }
}
