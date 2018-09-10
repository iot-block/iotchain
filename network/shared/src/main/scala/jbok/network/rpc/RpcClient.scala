package jbok.network.rpc

import cats.effect.ConcurrentEffect
import jbok.network.client.Client

import scala.language.experimental.macros

class RpcClient[F[_]: ConcurrentEffect](val client: Client[F, String]) {
  def useAPI[API]: API = macro RpcClientMacro.useAPI[API]
}

object RpcClient {
  def apply[F[_]: ConcurrentEffect](client: Client[F, String]): RpcClient[F] = new RpcClient[F](client)
}
