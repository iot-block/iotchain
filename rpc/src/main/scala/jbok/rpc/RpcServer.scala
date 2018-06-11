package jbok.rpc

import java.net.InetSocketAddress

import cats.effect._
import cats.implicits._
import fs2.StreamApp.ExitCode
import io.circe.syntax._
import fs2.{Scheduler, _}
import jbok.rpc.json._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware._
import org.http4s.{EntityDecoder, EntityEncoder, HttpService}

import scala.concurrent.ExecutionContext

final case class RpcServer[F[_]: Effect](addr: InetSocketAddress, handler: JsonrpcMsg => JsonrpcMsg)
    extends Http4sDsl[F] {
  implicit val encoder: EntityEncoder[F, JsonrpcMsg] = jsonEncoderOf[F, JsonrpcMsg]
  implicit val decoder: EntityDecoder[F, JsonrpcMsg] = jsonOf[F, JsonrpcMsg]

  val service: HttpService[F] = HttpService[F] {
    case req @ POST -> Root =>
      for {
        message <- req.as[JsonrpcMsg]
        resp <- Ok(handler(message).asJson)
      } yield resp
  }

  val endpoints: HttpService[F] = {
    val config = CORSConfig(
      anyOrigin = true,
      anyMethod = true,
      allowCredentials = true,
      maxAge = Long.MaxValue
    )
    CORS(service, config)
  }

  def server: F[Server[F]] = {
    BlazeBuilder[F]
      .bindSocketAddress(addr)
      .mountService(endpoints, "/")
      .start
  }

  def start(implicit ec: ExecutionContext): Stream[F, ExitCode] = {
    Scheduler[F](corePoolSize = 2).flatMap { implicit S =>
      for {
        exitCode <- BlazeBuilder[F]
          .bindSocketAddress(addr)
          .mountService(endpoints, "/")
          .serve
      } yield exitCode
    }
  }
}

object RpcServer {
  def defaultHandler(message: JsonrpcMsg): JsonrpcMsg = message match {
    case JsonrpcRequest(method, params, id) => JsonrpcResponse.methodNotFound(s"method ${method} not found", id)
    case JsonrpcNotification(method, params) => JsonrpcResponse.invalidRequest("notification")
    case JsonrpcResponse.Success(result, id) => JsonrpcResponse.invalidRequest("success response")
    case JsonrpcResponse.Error(error, id) => JsonrpcResponse.invalidRequest("error response")
    case JsonrpcResponse.Empty => JsonrpcResponse.invalidRequest("empty response")
  }

  def apply[F[_]: Effect](addr: InetSocketAddress): RpcServer[F] = {
    RpcServer[F](addr, defaultHandler _)
  }
}
