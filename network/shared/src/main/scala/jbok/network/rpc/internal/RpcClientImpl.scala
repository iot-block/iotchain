package jbok.network.rpc.internal

import cats.effect.Sync
import cats.implicits._
import jbok.codec._
import jbok.network.rpc.{RpcClient, RpcRequest, RpcResponse}

final class RpcClientImpl[F[_], P](client: RpcClient[F, P])(implicit F: Sync[F]) {
  def execute[A <: Product, B](path: List[String], arguments: A)(implicit encoder: Encoder[A, P], decoder: Decoder[B, P]): F[B] = {
    val encoded = encoder.encode(arguments)
    val request = RpcRequest(path, encoded)
    client.transport.fetch(request).flatMap {
      case RpcResponse.Success(payload) =>
        decoder.decode(payload) match {
          case Left(e)      => F.raiseError[B](e)
          case Right(value) => F.pure[B](value)
        }

      case RpcResponse.Failure(code, reason, data) =>
        F.raiseError[B](new Exception(s"client failure: code=${code},reason=${reason},data=${data}"))
    }
  }
}
