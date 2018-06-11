package jbok.rpc.json

import cats.effect.Effect
import cats.implicits._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

sealed trait JsonrpcMsg
object JsonrpcMsg {
  implicit val encoder: Encoder[JsonrpcMsg] = new Encoder[JsonrpcMsg] {
    override def apply(a: JsonrpcMsg): Json = {
      val json = a match {
        case r: JsonrpcRequest => r.asJson
        case r: JsonrpcNotification => r.asJson
        case r: JsonrpcResponse => r.asJson
      }
      json.mapObject(_.add("jsonrpc", "2.0".asJson))
    }
  }

  implicit val decoder: Decoder[JsonrpcMsg] =
    Decoder.decodeJsonObject.emap { obj =>
      val json = Json.fromJsonObject(obj)

      val result =
        if (obj.contains("id"))
          if (obj.contains("error")) json.as[JsonrpcResponse.Error]
          else if (obj.contains("result")) json.as[JsonrpcResponse.Success]
          else json.as[JsonrpcRequest]
        else json.as[JsonrpcNotification]
      result.leftMap(_.toString)
    }
}

@JsonCodec case class JsonrpcRequest(
    method: String,
    params: Option[Json],
    id: RequestId
) extends JsonrpcMsg {
  def toError(code: ErrorCode, message: String): JsonrpcResponse =
    JsonrpcResponse.error(ErrorObject(code, message, None), id)
}

@JsonCodec case class JsonrpcNotification(method: String, params: Option[Json]) extends JsonrpcMsg

sealed trait JsonrpcResponse extends JsonrpcMsg {
  def isSuccess: Boolean = this.isInstanceOf[JsonrpcResponse.Success]
}

object JsonrpcResponse {
  implicit val encoderResponse: Encoder[JsonrpcResponse] = new Encoder[JsonrpcResponse] {
    override def apply(a: JsonrpcResponse): Json = a match {
      case r: JsonrpcResponse.Success => r.asJson
      case r: JsonrpcResponse.Error => r.asJson
      case JsonrpcResponse.Empty => JsonObject.empty.asJson
    }
  }

  @JsonCodec case class Success(result: Json, id: RequestId) extends JsonrpcResponse
  @JsonCodec case class Error(error: ErrorObject, id: RequestId) extends JsonrpcResponse
  case object Empty extends JsonrpcResponse

  def empty: JsonrpcResponse = Empty

  def ok(result: Json, id: RequestId): JsonrpcResponse = success(result, id)

  def okAsync[F[_], T](value: T)(implicit F: Effect[F]): F[Either[JsonrpcResponse.Error, T]] =
    F.delay(Right(value))

  def success(result: Json, id: RequestId): JsonrpcResponse =
    Success(result, id)

  def error(error: ErrorObject, id: RequestId): JsonrpcResponse.Error =
    Error(error, id)

  def internalError(message: String): JsonrpcResponse.Error =
    internalError(message, RequestId.Null)

  def internalError(message: String, id: RequestId): JsonrpcResponse.Error =
    Error(ErrorObject(ErrorCode.InternalError, message, None), id)

  def invalidParams(message: String): JsonrpcResponse.Error =
    invalidParams(message, RequestId.Null)

  def invalidParams(message: String, id: RequestId): JsonrpcResponse.Error =
    Error(ErrorObject(ErrorCode.InvalidParams, message, None), id)

  def invalidRequest(message: String): JsonrpcResponse.Error =
    Error(
      ErrorObject(ErrorCode.InvalidRequest, message, None),
      RequestId.Null
    )

  def cancelled(id: Json): JsonrpcResponse.Error =
    Error(
      ErrorObject(ErrorCode.RequestCancelled, "", None),
      id.as[RequestId].getOrElse(RequestId.Null)
    )

  def parseError(message: String): JsonrpcResponse.Error =
    Error(ErrorObject(ErrorCode.ParseError, message, None), RequestId.Null)

  def methodNotFound(message: String, id: RequestId): JsonrpcResponse.Error =
    Error(ErrorObject(ErrorCode.MethodNotFound, message, None), id)
}
