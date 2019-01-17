package jbok.network.rpc

import cats.effect.ConcurrentEffect
import cats.implicits._
import io.circe.Json
import io.circe.parser._
import jbok.network.client.Client
import jbok.network.{Request, Response}

import scala.language.experimental.macros

class RpcClient[F[_]](doReq: String => F[String])(implicit F: ConcurrentEffect[F]) {
  private[this] val log = jbok.common.log.getLogger("RpcClient")

  def useAPI[API]: API = macro RpcClientMacro.useAPI[API]

  def request(request: Request[F]): F[Response[F]] =
    for {
      _   <- log.debug(s"sending req: ${request}").pure[F]
      req <- request.asText
      _ = log.debug(s"sending text req ${req}")
      text <- doReq(req)
      _ = log.debug(s"received text res ${text}")
      resp <- Response.fromText[F](text)
    } yield resp
}

object RpcClient {
  def apply[F[_]: ConcurrentEffect](client: Client[F]): RpcClient[F] =
    new RpcClient[F]((s: String) =>
      for {
        request <- Request.fromText[F](s)
        resp    <- client.request(request)
        text    <- resp.asText
      } yield text)

  def apply[F[_]: ConcurrentEffect](doReq: String => F[String]) = new RpcClient[F](doReq)
}
