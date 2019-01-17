package jbok.app.client

import java.net.URI
import java.util.UUID

import cats.effect.{ConcurrentEffect, ContextShift, IO}
import jbok.codec.rlp.implicits._
import jbok.common.execution._
import jbok.network.client.{Client, WsClient}
import jbok.network.rpc.RpcClient
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}

final case class JbokClient(
    uri: URI,
    client: Client[IO],
    public: PublicAPI[IO],
    personal: PersonalAPI[IO],
    admin: AdminAPI[IO]
)(implicit F: ConcurrentEffect[IO], cs: ContextShift[IO]) {

  private val rpcClient = RpcClient(client)

  def status: IO[Boolean] = client.haltWhenTrue.get.map(!_)
}

object JbokClient {
  import jbok.network.rpc.RpcService._

  def apply(uri: URI)(implicit F: ConcurrentEffect[IO]): IO[JbokClient] =
    for {
      client <- WsClient[IO](uri)
      public   = RpcClient(client).useAPI[PublicAPI[IO]]
      personal = RpcClient(client).useAPI[PersonalAPI[IO]]
      admin    = RpcClient(client).useAPI[AdminAPI[IO]]
      _ <- client.start
    } yield JbokClient(uri, client, public, personal, admin)
}
