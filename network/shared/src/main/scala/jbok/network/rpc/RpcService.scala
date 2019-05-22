package jbok.network.rpc

import cats.effect.Sync
import jbok.network.rpc.internal.RpcServiceMacro

import scala.language.experimental.macros

final class RpcService[F[_], P](val apiMap: RpcService.Map[F, P]) {
  def handle(request: RpcRequest[P]): ServiceResult[F, P] = {
    def notFoundFailure(path: List[String]): ServiceResult[F, P] =
      ServiceResult.Failure(None, ServerFailure.PathNotFound(request.path))

    request.path match {
      case apiName :: methodName :: Nil =>
        val function: Option[P => ServiceResult[F, P]] = apiMap.get(apiName).flatMap(_.get(methodName))
        function.fold[ServiceResult[F, P]](notFoundFailure(request.path)) { f =>
          f(request.payload)
        }
      case _ => notFoundFailure(request.path)
    }
  }

  def mount[API](impl: API)(implicit F: Sync[F]): RpcService[F, P] =
    macro RpcServiceMacro.impl[API, F, P]

  def orElse(name: String, value: RpcService.MapValue[F, P]): RpcService[F, P] =
    new RpcService(apiMap + (name -> value))
}

object RpcService {
  type MapValue[F[_], P] = collection.Map[String, P => ServiceResult[F, P]]

  type Map[F[_], P] = collection.Map[String, MapValue[F, P]]

  def apply[F[_], P]: RpcService[F, P] = new RpcService[F, P](collection.mutable.HashMap.empty)
}

sealed trait ServiceResult[F[_], P]

object ServiceResult {
  final case class Value[P](raw: Any, payload: P)
  final case class Success[F[_], P](argumentObject: Product, result: F[Value[P]])            extends ServiceResult[F, P]
  final case class Failure[F[_], P](argumentObject: Option[Product], failure: ServerFailure) extends ServiceResult[F, P]
}
