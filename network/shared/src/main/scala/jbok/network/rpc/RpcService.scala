package jbok.network.rpc

import cats.effect.Sync
import jbok.network.rpc.internal.RpcServiceMacro

import scala.language.experimental.macros

final class RpcService[F[_], P](val apiMap: RpcService.Map[F, P])(implicit F: Sync[F]) {
  def handle(request: RpcRequest[P]): F[RpcResponse[P]] = {
    def notFoundFailure(path: List[String]): F[RpcResponse[P]] =
      F.pure(RpcResponse.Failure[P](404, s"Path ${path.mkString("/")} not found"))

    request.path match {
      case apiName :: methodName :: Nil =>
        val function: Option[P => F[RpcResponse[P]]] = apiMap.get(apiName).flatMap(_.get(methodName))
        function.fold(notFoundFailure(apiName :: methodName :: Nil))(f => f(request.payload))

      case _ => notFoundFailure(request.path)
    }
  }

  def mount[API](impl: API)(implicit F: Sync[F]): RpcService[F, P] =
    macro RpcServiceMacro.impl[API, F, P]

  def orElse(name: String, value: RpcService.MapValue[F, P]): RpcService[F, P] =
    new RpcService(apiMap + (name -> value))
}

object RpcService {
  type MapValue[F[_], P] = collection.Map[String, P => F[RpcResponse[P]]]

  type Map[F[_], P] = collection.Map[String, MapValue[F, P]]

  def apply[F[_], P](implicit F: Sync[F]): RpcService[F, P] = new RpcService[F, P](collection.mutable.HashMap.empty)
}
