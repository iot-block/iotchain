package jbok.network.rpc

import _root_.io.circe._
import _root_.io.circe.syntax._
import cats.effect.{Concurrent, IO}
import fs2._
import jbok.network.rpc.jsonrpc._

import scala.language.experimental.macros

final class RpcService(
    val handlers: Map[String, Json => IO[Json]]
)(implicit F: Concurrent[IO]) {
  private[this] val log = jbok.common.log.getLogger("RpcServer")

  def mountAPI[API](api: API): RpcService = macro RpcServiceMacro.mountAPI[RpcService, API]

  def addHandlers(handlers: List[(String, Json => IO[Json])]): RpcService =
    new RpcService(this.handlers ++ handlers.toMap)

  def handleRequest(req: Json): IO[Json] = {
    log.debug(s"handling request:\n${req}")
    req.hcursor.downField("id").as[String] match {
      case Left(e) =>
        log.debug(s"invalid request, no id")
        IO.pure(RpcErrorResponse("", RpcErrors.invalidRequest).asJson)
      case Right(id) =>
        req.hcursor.downField("method").as[String] match {
          case Left(e) =>
            log.debug(s"invalid request, no method")
            IO.pure(RpcErrorResponse(id, RpcErrors.invalidRequest).asJson)
          case Right(method) =>
            handlers.get(method) match {
              case Some(handler) =>
                handler.apply(req).attempt.map {
                  case Left(e) =>
                    log.debug(s"request processing error", e)
                    RpcErrorResponse(id, RpcErrors.internalError).asJson
                  case Right(resp) =>
                    log.debug(s"success response:\n${resp}")
                    resp
                }
              case None =>
                log.debug(s"invalid request, method ${method} not found")
                IO.pure(RpcErrorResponse(id, RpcErrors.methodNotFound(method)).asJson)
            }
        }
    }
  }

  val pipe: Pipe[IO, Json, Json] = _.evalMap(handleRequest)
}

object RpcService {
  def apply(handlers: Map[String, Json => IO[Json]] = Map.empty)(implicit F: Concurrent[IO]): RpcService =
    new RpcService(handlers)
}
