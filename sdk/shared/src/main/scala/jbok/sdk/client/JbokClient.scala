package jbok.sdk.client

import java.net.URI
import java.util.UUID

import cats.effect.{ContextShift, IO}
import jbok.network.client.Client
import jbok.network.rpc.RpcClient
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}

import scala.annotation.meta.field
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("JbokClientClass")
case class JbokClient(@(JSExport @field) uri: URI,
                      @(JSExport @field) client: Client[IO, String],
                      @(JSExport @field) public: PublicAPI[IO],
                      @(JSExport @field) personal: PersonalAPI[IO],
                      @(JSExport @field) admin: AdminAPI[IO])(implicit cs: ContextShift[IO]) {

  private val rpcClient = RpcClient(client)

  @JSExport
  def status: IO[Boolean] = client.haltWhenTrue.get.map(!_)

  @JSExport
  def jsonrpc(json: String, id: String = UUID.randomUUID().toString): IO[String] =
    rpcClient.jsonrpc(json, id)
}
