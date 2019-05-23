package jbok.network.http

import cats.effect.ConcurrentEffect
import io.circe.Json
import jbok.network.rpc.{RpcRequest, RpcResponse, RpcTransport}
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

final class HttpTransport[F[_]](baseUri: String, client: Client[F])(implicit F: ConcurrentEffect[F]) extends RpcTransport[F, Json] {
  override def fetch(request: RpcRequest[Json]): F[RpcResponse[Json]] = {
    val uri = request.path.foldLeft(Uri.unsafeFromString(baseUri))(_ / _)
    val req = Request[F](Method.POST, uri = uri).withEntity(request.payload)
    client.fetchAs[RpcResponse[Json]](req)
  }
}
