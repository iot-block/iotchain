package jbok.network.rpc

import cats.effect.ConcurrentEffect
import cats.implicits._
import io.circe.Json
import io.circe.parser._
import jbok.network.client.Client

import scala.language.experimental.macros

class RpcClient[F[_]](doReq: String => F[String])(implicit F: ConcurrentEffect[F]) {
  def useAPI[API]: API = macro RpcClientMacro.useAPI[API]

  def jsonrpc(request: Json): F[Json] =
    doReq(request.noSpaces).flatMap(resp => F.fromEither(parse(resp)))
}

object RpcClient {
  def apply[F[_]: ConcurrentEffect](client: Client[F, String]): RpcClient[F] = new RpcClient[F](client.request)

  def apply[F[_]: ConcurrentEffect](doReq: String => F[String]) = new RpcClient[F](doReq)
}
