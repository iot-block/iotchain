package jbok.network.rpc

import jbok.network.rpc.internal.RpcClientMacro

import scala.language.experimental.macros

final class RpcClient[F[_], P](
    val transport: RpcTransport[F, P],
    val logger: RpcLogHandler[F]
) {
  def use[API]: API = macro RpcClientMacro.impl[API, F, P]
}

object RpcClient {
  def noopLogHandler[F[_]]: RpcLogHandler[F] = new RpcLogHandler[F] {
    override def logRequest[A](path: List[String], arguments: Product, result: F[A]): F[A] = result
  }

  def apply[F[_], P](transport: RpcTransport[F, P]) = new RpcClient[F, P](transport, noopLogHandler)
}
