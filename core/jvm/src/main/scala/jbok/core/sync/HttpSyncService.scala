package jbok.core.sync

import cats.effect.{ConcurrentEffect, Resource, Timer}
import cats.implicits._
import fs2._
import jbok.core.config.Configs.SyncConfig
import jbok.core.messages._
import jbok.network.{Request, Response}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder

final class HttpSyncService[F[_]](config: SyncConfig, syncService: SyncService[F])(implicit F: ConcurrentEffect[F], T: Timer[F]) extends Http4sDsl[F] {
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / GetBlockHeadersByNumber.name =>
      for {
        request  <- Request.fromHttp4s(req)
        r        <- request.as[GetBlockHeadersByNumber]
        headers  <- syncService.getBlockHeadersByNumber(r.start, r.limit)
        response <- Response.ok[F, BlockHeaders](request.id, headers)
      } yield Response.toHttp4s(response)

    case req @ POST -> Root / GetBlockBodies.name =>
      for {
        request  <- Request.fromHttp4s(req)
        r        <- request.as[GetBlockBodies]
        bodies   <- syncService.getBlockBodies(r.hashes)
        response <- Response.ok[F, BlockBodies](request.id, bodies)
      } yield Response.toHttp4s(response)
  }

  val resource: Resource[F, Server[F]] =
    BlazeServerBuilder[F]
      .bindHttp(config.port, config.host)
      .withHttpApp(routes.orNotFound)
      .withoutBanner
      .resource

  val stream: Stream[F, Unit] =
    Stream.resource(resource).void
}
