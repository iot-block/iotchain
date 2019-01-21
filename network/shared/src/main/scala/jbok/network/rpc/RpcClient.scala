package jbok.network.rpc

import cats.effect.ConcurrentEffect
import cats.implicits._
import jbok.network.client.Client
import jbok.network.{Request, Response}
import io.circe._
import io.circe.parser._

import scala.language.experimental.macros

class RpcClient[F[_]](doReq: String => F[String])(implicit F: ConcurrentEffect[F]) {
  private[this] val log = jbok.common.log.getLogger("RpcClient")

  def useAPI[API]: API = macro RpcClientMacro.useAPI[API]

  def request(request: Request[F]): F[Response[F]] =
    for {
      _   <- log.debug(s"sending req: ${request}").pure[F]
      req <- request.asJson
      _ = log.debug(s"sending json req ${req}")
      text <- doReq(req.noSpaces)
      _ = log.debug(s"received json res ${text}")
      resp <- Response.fromJson[F](parse(text).getOrElse(Json.Null))
    } yield resp
}

object RpcClient {
  def apply[F[_]: ConcurrentEffect](client: Client[F]): RpcClient[F] =
    new RpcClient[F]((s: String) =>
      for {
        request <- Request.fromJson[F](parse(s).getOrElse(Json.Null))
        resp    <- client.request(request)
        text    <- resp.asJson.map(_.noSpaces)
      } yield text)

  def apply[F[_]: ConcurrentEffect](doReq: String => F[String]) = new RpcClient[F](doReq)
}
