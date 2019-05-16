package jbok.network.http

import cats.effect.{ConcurrentEffect, ContextShift}
import io.circe.Json
import jbok.network.rpc.{RpcRequest, RpcTransport}
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.http4s.circe.CirceEntityCodec._

final class HttpTransport[F[_]](baseUri: String, client: Client[F])(implicit F: ConcurrentEffect[F], cs: ContextShift[F]) extends RpcTransport[F, Json] {
  override def fetch(request: RpcRequest[Json]): F[Json] = {
    val uri = request.path.foldLeft(Uri.unsafeFromString(baseUri))(_ / _)
    val req = Request[F](Method.POST, uri = uri).withEntity(request.payload)
    HttpClients.okHttp[F].use { client =>
      client.fetchAs[Json](req)
    }
  }
}
