package jbok.app

import java.net.URI

import cats.effect.IO
import jbok.app.api.{PrivateAPI, PublicAPI}
import jbok.network.client.{Client, WSClientBuilderPlatform}
import jbok.network.rpc.RpcClient

case class JbokClient(uri: URI, admin: PrivateAPI, public: PublicAPI)

object JbokClient {
  import jbok.network.rpc.RpcServer._
  def apply(uri: URI): IO[JbokClient] =
    for {
      client <- Client(WSClientBuilderPlatform[IO, String], uri)
      admin = RpcClient(client).useAPI[PrivateAPI]
      public = RpcClient(client).useAPI[PublicAPI]
      _ <- client.start
    } yield JbokClient(uri, admin, public)
}
