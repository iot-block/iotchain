package jbok.rpc.http

import cats.effect._
import cats.implicits._
import io.circe.syntax._
import jbok.rpc.json._
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

object JsonRpcService {

  def handle(message: JsonRpcMsg): JsonRpcMsg = message match {
    case JsonRpcRequest(method, params, id) => JsonRpcResponse.methodNotFound(s"method ${method} not found", id)
    case JsonRpcNotification(method, params) => JsonRpcResponse.invalidRequest("notification")
    case JsonRpcResponse.Success(result, id) => JsonRpcResponse.invalidRequest("success response")
    case JsonRpcResponse.Error(error, id) => JsonRpcResponse.invalidRequest("error response")
    case JsonRpcResponse.Empty => JsonRpcResponse.invalidRequest("empty response")
  }

  def service[F[_]](implicit F: Effect[F]): HttpService[F] = {
    implicit val msgDecoder = jsonOf[F, JsonRpcMsg]

    val dsl = Http4sDsl[F]
    import dsl._

    HttpService[F] {
      case req @ POST -> Root / "jsonrpc" =>
        for {
          message <- req.as[JsonRpcMsg](F, msgDecoder)
          resp <- Ok(handle(message).asJson)
        } yield resp
    }
  }
}
