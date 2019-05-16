package jbok.network.rpc.internal

import cats.effect.Sync
import cats.implicits._
import jbok.codec._
import jbok.network.rpc.{RpcClient, RpcRequest}

final class RpcClientImpl[F[_], Payload](client: RpcClient[F, Payload])(implicit F: Sync[F]) {
  def execute[A <: Product, B](path: List[String], arguments: A)(
      implicit encoder: Encoder[A, Payload], decoder: Decoder[B, Payload]
  ): F[B] = {
    val encoded = encoder.encode(arguments)
    val request = RpcRequest(path, encoded)
    val result = client.transport.fetch(request).flatMap { payload =>
      decoder.decode(payload) match {
        case Left(e)      => F.raiseError[B](e)
        case Right(value) => F.pure[B](value)
      }
    }
    client.logger.logRequest(path, arguments, result)
  }
}
