package jbok.app.client

import java.net.URI

import cats.effect.IO
import jbok.app.api.{AdminAPI, PersonalAPI, PublicAPI}
import jbok.common.execution._
import jbok.network.client.{Client, WsClient}
import jbok.network.rpc.RpcClient

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("JbokClientClass")
case class JbokClient(uri: URI,
                      client: Client[IO, String],
                      public: PublicAPI[IO],
                      personal: PersonalAPI[IO],
                      admin: AdminAPI[IO]) {
  def status: IO[Boolean] = client.haltWhenTrue.get.map(!_)
}

@JSExportTopLevel("JBokClient")
object JbokClient {
  import jbok.network.rpc.RpcServer._

  def apply(uri: URI): IO[JbokClient] =
    for {
      client <- WsClient[IO, String](uri)
      public   = RpcClient(client).useAPI[PublicAPI[IO]]
      personal = RpcClient(client).useAPI[PersonalAPI[IO]]
      admin    = RpcClient(client).useAPI[AdminAPI[IO]]
      _ <- client.start
    } yield JbokClient(uri, client, public, personal, admin)

  @JSExport
  def webSocket(url: String): IO[JbokClient] = {
    val uri = new URI(url)
    apply(uri)
  }
}
