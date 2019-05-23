package jbok.network.rpc

import cats.effect.{Clock, Sync}
import cats.implicits._
import jbok.common.log.Logger
import jbok.network.rpc.internal.RpcClientMacro

import scala.concurrent.duration._
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

  def defaultLogHandler[F[_]](implicit F: Sync[F], clock: Clock[F]): RpcLogHandler[F] = new RpcLogHandler[F] {
    val log = Logger[F]
    override def logRequest[A](path: List[String], arguments: Product, result: F[A]): F[A] =
      for {
        tic <- clock.monotonic(MILLISECONDS)
        _   <- log.i(s"--> ${RpcLogHandler.requestLogLine(path, arguments)}")
        r   <- result
        toc <- clock.monotonic(MILLISECONDS)
        _   <- log.i(s"<-- ${RpcLogHandler.requestLogLine(path, arguments, r)} in ${toc - tic}ms")
      } yield r
  }

  def apply[F[_], P](transport: RpcTransport[F, P]) = new RpcClient[F, P](transport, noopLogHandler)
}
