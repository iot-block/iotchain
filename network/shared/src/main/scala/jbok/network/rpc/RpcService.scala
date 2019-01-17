package jbok.network.rpc

import cats.effect.{Concurrent, IO}
import fs2._
import jbok.network.{Message, Request, Response}

import scala.language.experimental.macros

final class RpcService(
    val handlers: Map[String, Request[IO] => IO[Response[IO]]]
)(implicit F: Concurrent[IO]) {
  private[this] val log = jbok.common.log.getLogger("RpcServer")

  def mountAPI[API](api: API): RpcService = macro RpcServiceMacro.mountAPI[RpcService, API]

  def addHandlers(handlers: List[(String, Request[IO] => IO[Response[IO]])]): RpcService =
    new RpcService(this.handlers ++ handlers.toMap)

  def handle(msg: Message[IO]): IO[Message[IO]] = msg match {
    case req: Request[IO] =>
      handlers.get(req.method) match {
        case Some(handler) =>
          handler(req).attempt.map {
            case Left(e) =>
              log.debug(s"request processing error", e)
              Response.internalError[IO](req.id)
            case Right(resp) =>
              log.debug(s"success response:\n${resp}")
              resp
          }
        case None =>
          log.debug(s"invalid request, method ${req.method} not found")
          IO.pure(Response.notFound[IO](req.id, req.method))

      }

    case res: Response[IO] =>
      IO.pure(Response.badRequest[IO](res.id))
  }

  val pipe: Pipe[IO, Message[IO], Message[IO]] = _.evalMap(handle)
}

object RpcService {
  def apply(handlers: Map[String, Request[IO] => IO[Response[IO]]] = Map.empty)(
      implicit F: Concurrent[IO]): RpcService =
    new RpcService(handlers)
}
