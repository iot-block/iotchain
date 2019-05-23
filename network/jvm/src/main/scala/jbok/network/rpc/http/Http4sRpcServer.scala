package jbok.network.rpc.http

import cats.effect.{ConcurrentEffect, Resource, Sync, Timer}
import cats.implicits._
import io.circe.Json
import io.circe.syntax._
import jbok.network.rpc.{RpcRequest, RpcService}
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder

object Http4sRpcServer {
  def routes[F[_]](service: RpcService[F, Json])(implicit F: Sync[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case req @ POST -> path =>
        for {
          json   <- req.as[Json]
          result <- service.handle(RpcRequest(path.toList, json))
          resp   <- Ok(result.asJson)
        } yield resp
    }
  }

  def server[F[_]](service: RpcService[F, Json])(implicit F: ConcurrentEffect[F], T: Timer[F]): Resource[F, Server[F]] =
    BlazeServerBuilder[F]
      .bindLocal(0)
      .withHttpApp(routes[F](service).orNotFound)
      .withWebSockets(true)
      .resource
}
