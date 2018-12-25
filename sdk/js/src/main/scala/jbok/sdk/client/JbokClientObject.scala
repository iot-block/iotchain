package jbok.sdk.client

import java.net.URI

import cats.effect.IO
import jbok.network.client.Client
import jbok.network.rpc.RpcClient
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}
import jbok.network.client.WsClientNode
import jbok.common.execution._
import jbok.codec.rlp.implicits._

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("JBokClient")
object JbokClientObject {
  import jbok.network.rpc.RpcServer._

  private def getJbokClient(uri: URI, client: Client[IO, String]): IO[JbokClient] = {
    val public   = RpcClient(client).useAPI[PublicAPI[IO]]
    val personal = RpcClient(client).useAPI[PersonalAPI[IO]]
    val admin    = RpcClient(client).useAPI[AdminAPI[IO]]
    client.start.map(_ => JbokClient(uri, client, public, personal, admin))
  }

  @JSExport
  def webSocket(url: String): IO[JbokClient] = {
    val uri = new URI(url)
    for {
      client     <- WsClientNode[IO, String](uri)
      jbokClient <- getJbokClient(uri, client)
    } yield jbokClient
  }
}
