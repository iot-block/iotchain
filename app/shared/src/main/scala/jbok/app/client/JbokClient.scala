package jbok.app.client

import java.net.URI

import cats.effect.IO
import jbok.network.client.{Client, WsClient}
import jbok.network.rpc.RpcClient
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}
import jbok.sdk.client.{JbokClient => sdkClient}
import jbok.common.execution._

object JbokClient {
  import jbok.network.rpc.RpcServer._

  private def getJbokClient(uri: URI, client: Client[IO, String]): IO[sdkClient] = {
    val public   = RpcClient(client).useAPI[PublicAPI[IO]]
    val personal = RpcClient(client).useAPI[PersonalAPI[IO]]
    val admin    = RpcClient(client).useAPI[AdminAPI[IO]]
    client.start.map(_ => sdkClient(uri, client, public, personal, admin))
  }

  def apply(uri: URI): IO[sdkClient] =
    for {
      client     <- WsClient[IO, String](uri)
      jbokClient <- getJbokClient(uri, client)
    } yield jbokClient
}
