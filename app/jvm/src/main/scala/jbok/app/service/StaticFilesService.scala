package jbok.app.service

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO}
import org.http4s.{HttpRoutes, Request, Response, StaticFile}
import org.http4s.dsl.io._

import scala.concurrent.ExecutionContext

object StaticFilesService {
  private val swaggerUiDir = "/swagger-ui"

  val blockingEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def static(path: String, req: Request[IO])(implicit cs: ContextShift[IO]): IO[Response[IO]] =
    StaticFile.fromResource(path, blockingEc, Some(req)).getOrElseF(NotFound())

  /**
    * Routes for getting static resources. These might be served more efficiently by apache2 or nginx,
    * but its nice to keep it self contained
    */
  def routes(implicit cs: ContextShift[IO]): HttpRoutes[IO] = HttpRoutes.of {
    case req @ GET -> Root  => static(swaggerUiDir + "/index.html", req)
    case req @ GET -> Root / path if List(".js", ".css", ".map", ".html", ".webm").exists(path.endsWith) =>
      static(s"${swaggerUiDir}/${path}", req)
  }
}
