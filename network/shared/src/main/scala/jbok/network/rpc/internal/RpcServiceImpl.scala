package jbok.network.rpc.internal

import cats.effect.Sync
import cats.implicits._
import jbok.codec._
import jbok.common.log.Logger
import jbok.network.rpc.RpcResponse

final class RpcServiceImpl[F[_], P](implicit F: Sync[F]) {
  private[this] val log = Logger[F]

  def execute[A <: Product, B](payload: P)(call: A => F[B])(implicit decoder: Decoder[A, P], encoder: Encoder[B, P]): F[RpcResponse[P]] =
    decoder.decode(payload) match {
      case Left(err) =>
        log.info(s"decode error:${err}") >>
          F.pure(RpcResponse.Failure[P](400, s"decode error"))

      case Right(arguments) =>
        call(arguments).map { result =>
          RpcResponse.Success(encoder.encode(result))
        }
    }
}
