package jbok.sdk.client

import java.net.URI

import cats.effect.IO
import jbok.common.execution._
import jbok.core.models.Block
import jbok.network.client.{Client, WsClient}
import jbok.network.rpc.RpcClient
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}

import scala.annotation.meta.field
import scala.scalajs.js.annotation.{JSExport, JSExportAll, JSExportTopLevel}

@JSExportTopLevel("JbokClientClass")
case class JbokClient(@(JSExport @field) uri: URI,
                      @(JSExport @field) client: Client[IO, String],
                      @(JSExport @field) public: PublicAPI[IO],
                      @(JSExport @field) personal: PersonalAPI[IO],
                      @(JSExport @field) admin: AdminAPI[IO]) {
  @JSExport
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

  @JSExport
  def getBlockNumber2(url: String): IO[Option[Block]] =
    for {
      jbc   <- webSocket(url)
      block <- jbc.public.getBlockByNumber(2)
    } yield block
}
