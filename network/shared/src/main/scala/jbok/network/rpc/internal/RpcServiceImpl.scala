package jbok.network.rpc.internal

import cats.effect.Sync
import cats.implicits._
import jbok.codec._
import jbok.network.rpc.{ServerFailure, ServiceResult}

final class RpcServiceImpl[F[_], P](implicit F: Sync[F]) {
  def execute[A <: Product, B](path: List[String], payload: P)(call: A => F[B])(implicit decoder: Decoder[A, P], encoder: Encoder[B, P]): ServiceResult[F, P] =
    decoder.decode(payload) match {
      case Left(err) =>
        ServiceResult.Failure[F, P](None, ServerFailure.DecodeError(err))

      case Right(arguments) =>
        val result = call(arguments).map { result =>
          ServiceResult.Value(result, encoder.encode(result))
        }
        ServiceResult.Success[F, P](arguments, result)
    }
}
