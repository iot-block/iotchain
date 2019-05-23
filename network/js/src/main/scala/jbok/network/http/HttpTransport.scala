package jbok.network.http

import cats.effect.Async
import cats.implicits._
import io.circe.Json
import jbok.network.rpc._
import io.circe.parser._

object HttpTransport {
  def apply[F[_]](baseUri: String)(implicit F: Async[F]): RpcTransport[F, Json] =
    new RpcTransport[F, Json] {
      override def fetch(request: RpcRequest[Json]): F[RpcResponse[Json]] = {
        val uri = (baseUri :: request.path).mkString("/")
        for {
          response <- HttpClient.post[F](uri, request.payload.noSpaces)
          resp     <- F.fromEither(decode[RpcResponse[Json]](response.data))
        } yield resp
      }
    }
}
