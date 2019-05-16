package jbok.network.rpc.http

import cats.data.EitherT
import cats.effect.{ConcurrentEffect, Resource, Sync, Timer}
import cats.implicits._
import com.offbynull.portmapper.mappers.upnpigd.externalmessages.RootUpnpIgdResponse.ServiceReference
import io.circe.Json
import io.circe
import io.circe.generic.JsonCodec
import io.circe.syntax._
import jbok.network.rpc.{RpcRequest, RpcService, ServerFailure, ServiceResult}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import fs2._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder

@JsonCodec
final case class HttpResponse(
    code: Int,
    message: String,
    data: Json
)

//object HttpResponse {
//  def ok(data: Json): [Response[IO]] =
//    Ok(HttpResponse(Status.Ok.code, "", data).asJson)
//
//  def badRequest(message: String): IO[Response[IO]] =
//    Ok(HttpResponse(Status.BadRequest.code, message, Json.Null).asJson)
//
//  def internalError(message: String): IO[Response[IO]] =
//    Ok(HttpResponse(Status.InternalServerError.code, message, Json.Null).asJson)
//}

//class Http4sRpcRoute[F[_]](implicit F: Sync[F]) {
//  object dsl extends Http4sDsl[F]
//  import dsl._
//
//  def fromService(service: RpcService[F, Json]): HttpRoutes[F] =
//    HttpRoutes.of[F] {
//      case req @ POST -> path =>
//        val resp = for {
//          body <- req.bodyAsText.compile.foldMonoid.attemptT.leftSemiflatMap(e => Ok(HttpResponse(BadRequest.code, e.getMessage, Json.Null).asJson))
//          json <- F.fromEither(circe.parser.parse(body)).attemptT.leftSemiflatMap(e => Ok(HttpResponse(BadRequest.code, e.getMessage, Json.Null).asJson))
//          handler <- service(RpcRequest(path.toList, json))
//            .attemptT
//            .leftSemiflatMap {
//              case x: ServerFailure.PathNotFound  => HttpResponse.badRequest(x.toString)
//              case ServerFailure.DecodeError(e)   => HttpResponse.badRequest(e.getMessage)
//              case ServerFailure.InternalError(e) => HttpResponse.internalError(e.getMessage)
//            }
//          result <- handler.attemptT.leftSemiflatMap(e => HttpResponse.internalError(e.getMessage))
//          resp   <- EitherT.liftF[F, Response[F], Response[F]](Ok(HttpResponse(Ok.code, "", result).asJson))
//        } yield resp
//
//        resp.fold(identity, identity)
//    }
//}

object Http4sRpcServer {
  def routes[F[_]](service: RpcService[F, Json])(implicit F: Sync[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case req @ POST -> path =>
        for {
          body <- req.bodyAsText.compile.foldMonoid
          json <- F.fromEither(circe.parser.parse(body))
          result <- service.handle(RpcRequest(path.toList, json)) match {
//            case ServiceResult.Failure(argumentObject, failure) =>
            case ServiceResult.Success(argumentObject, result) =>
              result.map(_.payload)
          }
          resp <- Ok(result)
        } yield resp
    }
  }

  def server[F[_]](service: RpcService[F, Json])(implicit F: ConcurrentEffect[F], T: Timer[F]): Resource[F, Server[F]] =
    BlazeServerBuilder[F]
      .withHttpApp(routes[F](service).orNotFound)
      .withWebSockets(true)
      .resource
}
