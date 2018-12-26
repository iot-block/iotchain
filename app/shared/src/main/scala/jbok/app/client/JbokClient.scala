package jbok.app.client

import java.net.URI
import java.util.UUID

import cats.effect.{ConcurrentEffect, ContextShift, IO}
import jbok.codec.rlp.implicits._
import jbok.common.execution._
import jbok.network.client.{Client, WsClient}
import jbok.network.rpc.RpcClient
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}

case class JbokClient(
    uri: URI,
    client: Client[IO, String],
    public: PublicAPI[IO],
    personal: PersonalAPI[IO],
    admin: AdminAPI[IO]
)(implicit F: ConcurrentEffect[IO], cs: ContextShift[IO]) {

  private val rpcClient = RpcClient(client)

  def status: IO[Boolean] = client.haltWhenTrue.get.map(!_)

  def jsonrpc(json: String, id: String = UUID.randomUUID().toString): IO[String] =
    rpcClient.jsonrpc(json, id)
}

object JbokClient {
  import jbok.network.rpc.RpcServer._

  def apply(uri: URI)(implicit F: ConcurrentEffect[IO]): IO[JbokClient] =
    for {
      client <- WsClient[IO, String](uri)
      public   = RpcClient(client).useAPI[PublicAPI[IO]]
      personal = RpcClient(client).useAPI[PersonalAPI[IO]]
      admin    = RpcClient(client).useAPI[AdminAPI[IO]]
      _ <- client.start
    } yield JbokClient(uri, client, public, personal, admin)
}
