package jbok.network.rpc

import java.util.UUID

import cats.effect.ConcurrentEffect
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import jbok.network.client.Client

import scala.language.experimental.macros

class RpcClient[F[_]: ConcurrentEffect](doReq: String => F[String]) {
  private[this] val log = jbok.common.log.getLogger("RpcClient")
  def useAPI[API]: API = macro RpcClientMacro.useAPI[API]

  def jsonrpc(json: String, id: String = UUID.randomUUID().toString): F[String] =
    doReq {
      parse(json).getOrElse(Json.Null).deepMerge(Json.obj(("id", id.asJson))).noSpaces
    }
}

object RpcClient {
  def apply[F[_]: ConcurrentEffect](client: Client[F, String]): RpcClient[F] = new RpcClient[F](client.request)

  def apply[F[_]: ConcurrentEffect](doReq: String => F[String]) = new RpcClient[F](doReq)
}
