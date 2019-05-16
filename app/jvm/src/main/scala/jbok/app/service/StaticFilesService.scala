package jbok.app.service

import java.util.concurrent.Executors

import cats.effect.{ContextShift, Sync}
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Request, Response, StaticFile, Status}

import scala.concurrent.ExecutionContext

object StaticFilesService {
  private val swaggerUiDir = "/swagger-ui"

  private val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  def static[F[_]: Sync](path: String, req: Request[F])(implicit cs: ContextShift[F]): F[Response[F]] =
    StaticFile.fromResource(path, blockingEC, Some(req)).getOrElse(Response[F](Status.NotFound))

  /**
    * Routes for getting static resources. These might be served more efficiently by apache2 or nginx,
    * but its nice to keep it self contained
    */
  def routes[F[_]](implicit F: Sync[F], cs: ContextShift[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case req @ GET -> Root => static(swaggerUiDir + "/index.html", req)
      case req @ GET -> Root / path if List(".js", ".css", ".map", ".html", ".webm").exists(path.endsWith) =>
        static(s"${swaggerUiDir}/${path}", req)
    }
  }
}
