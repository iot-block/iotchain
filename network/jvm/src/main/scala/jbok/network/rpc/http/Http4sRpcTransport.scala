package jbok.network.rpc.http

import cats.effect.ConcurrentEffect
import jbok.network.rpc.{RpcRequest, RpcResponse, RpcTransport}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.{EntityDecoder, EntityEncoder, Method, Request, Uri}

import scala.concurrent.ExecutionContext

final class Http4sRpcTransport[F[_], P](
    baseUri: Uri
)(implicit F: ConcurrentEffect[F], entityEncoder: EntityEncoder[F, P], entityDecoder: EntityDecoder[F, RpcResponse[P]])
    extends RpcTransport[F, P] {

  override def fetch(request: RpcRequest[P]): F[RpcResponse[P]] = {
    val uri = request.path.foldLeft(baseUri)(_ / _)

    val req = Request[F](Method.POST, uri = uri).withEntity(request.payload)

    BlazeClientBuilder[F](ExecutionContext.global).resource.use { client =>
      client.fetchAs[RpcResponse[P]](req)
    }
  }
}
