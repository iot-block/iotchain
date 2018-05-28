package jbok.rpc.json

import cats.effect.Effect
import cats.implicits._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

sealed trait JsonRpcMsg
object JsonRpcMsg {
  implicit val encoder: Encoder[JsonRpcMsg] = new Encoder[JsonRpcMsg] {
    override def apply(a: JsonRpcMsg): Json = {
      val json = a match {
        case r: JsonRpcRequest => r.asJson
        case r: JsonRpcNotification => r.asJson
        case r: JsonRpcResponse => r.asJson
      }
      json.mapObject(_.add("jsonrpc", "2.0".asJson))
    }
  }

  implicit val decoder: Decoder[JsonRpcMsg] =
    Decoder.decodeJsonObject.emap { obj =>
      val json = Json.fromJsonObject(obj)
      val result =
        if (obj.contains("id"))
          if (obj.contains("error")) json.as[JsonRpcResponse.Error]
          else if (obj.contains("result")) json.as[JsonRpcResponse.Success]
          else json.as[JsonRpcRequest]
        else json.as[JsonRpcNotification]
      result.leftMap(_.toString)
    }
}

@JsonCodec case class JsonRpcRequest(
    method: String,
    params: Option[Json],
    id: RequestId
) extends JsonRpcMsg {
  def toError(code: ErrorCode, message: String): JsonRpcResponse =
    JsonRpcResponse.error(ErrorObject(code, message, None), id)
}

@JsonCodec case class JsonRpcNotification(method: String, params: Option[Json]) extends JsonRpcMsg

sealed trait JsonRpcResponse extends JsonRpcMsg {
  def isSuccess: Boolean = this.isInstanceOf[JsonRpcResponse.Success]
}

object JsonRpcResponse {
  implicit val encoderResponse: Encoder[JsonRpcResponse] = new Encoder[JsonRpcResponse] {
    override def apply(a: JsonRpcResponse): Json = a match {
      case r: JsonRpcResponse.Success => r.asJson
      case r: JsonRpcResponse.Error => r.asJson
      case JsonRpcResponse.Empty => JsonObject.empty.asJson
    }
  }

  @JsonCodec case class Success(result: Json, id: RequestId) extends JsonRpcResponse
  @JsonCodec case class Error(error: ErrorObject, id: RequestId) extends JsonRpcResponse
  case object Empty extends JsonRpcResponse

  def empty: JsonRpcResponse = Empty

  def ok(result: Json, id: RequestId): JsonRpcResponse = success(result, id)

  def okAsync[F[_], T](value: T)(implicit F: Effect[F]): F[Either[JsonRpcResponse.Error, T]] =
    F.delay(Right(value))

  def success(result: Json, id: RequestId): JsonRpcResponse =
    Success(result, id)

  def error(error: ErrorObject, id: RequestId): JsonRpcResponse.Error =
    Error(error, id)

  def internalError(message: String): JsonRpcResponse.Error =
    internalError(message, RequestId.Null)

  def internalError(message: String, id: RequestId): JsonRpcResponse.Error =
    Error(ErrorObject(ErrorCode.InternalError, message, None), id)

  def invalidParams(message: String): JsonRpcResponse.Error =
    invalidParams(message, RequestId.Null)

  def invalidParams(message: String, id: RequestId): JsonRpcResponse.Error =
    Error(ErrorObject(ErrorCode.InvalidParams, message, None), id)

  def invalidRequest(message: String): JsonRpcResponse.Error =
    Error(
      ErrorObject(ErrorCode.InvalidRequest, message, None),
      RequestId.Null
    )

  def cancelled(id: Json): JsonRpcResponse.Error =
    Error(
      ErrorObject(ErrorCode.RequestCancelled, "", None),
      id.as[RequestId].getOrElse(RequestId.Null)
    )

  def parseError(message: String): JsonRpcResponse.Error =
    Error(ErrorObject(ErrorCode.ParseError, message, None), RequestId.Null)

  def methodNotFound(message: String, id: RequestId): JsonRpcResponse.Error =
    Error(ErrorObject(ErrorCode.MethodNotFound, message, None), id)
}
