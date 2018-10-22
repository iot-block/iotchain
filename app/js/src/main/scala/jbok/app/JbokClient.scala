package jbok.app

import java.net.URI

import cats.effect.IO
import fs2._
import jbok.app.api.{PrivateAPI, PublicAPI}
import jbok.network.client.{Client, WSClientBuilderPlatform}
import jbok.common.execution._
import jbok.network.rpc.RpcClient

import scala.concurrent.duration._

case class JbokClient(uri: URI, client: Client[IO, String], admin: PrivateAPI, public: PublicAPI) {
  def status: Stream[IO, Boolean] = for {
    _ <- Stream.awakeEvery[IO](5.seconds)
    isUp <- Stream.eval(client.isUp)
  } yield isUp
}

object JbokClient {
  import jbok.network.rpc.RpcServer._
  def apply(uri: URI): IO[JbokClient] =
    for {
      client <- Client(WSClientBuilderPlatform[IO, String], uri)
      admin = RpcClient(client).useAPI[PrivateAPI]
      public = RpcClient(client).useAPI[PublicAPI]
      _ <- client.start
    } yield JbokClient(uri, client, admin, public)
}
