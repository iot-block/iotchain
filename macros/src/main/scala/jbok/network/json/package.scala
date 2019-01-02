package jbok.network

import io.circe._
import io.circe.syntax._
import io.circe.Decoder.decodeOption
import io.circe.Encoder.encodeOption

package object json {
  final case class JsonRpcRequest[A](id: String, method: String, params: A)
  final case class JsonRpcNotification[A](method: String, params: A)
  final case class JsonRpcResultResponse[A](id: String, result: A)
  final case class JsonRpcErrorResponse[+A](id: String, error: JsonRpcError[A])
  final case class JsonRpcError[+A](code: Int, message: String, data: Option[A]) extends Exception(message)

  object JsonRpcErrors {
    val parseError =
      JsonRpcError(
        -32700,
        "Parse error",
        Some("Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text."))
    val invalidRequest =
      JsonRpcError(-32600, "Invalid Request", Some("The JSON sent is not a valid Request object."))
    val methodNotFound =
      JsonRpcError(-32601, "Method not found", Some("The method does not exist / is not available."))
    val invalidParams =
      JsonRpcError(-32602, "Invalid params", Some("Invalid method parameter(s)."))
    val internalError =
      JsonRpcError(-32603, "Internal error", Some("Internal JSON-RPC error."))
  }

  // JSONRPCRequest
  implicit def encodeJsonRpcRequest[A](implicit encoder: Encoder[A]): Encoder[JsonRpcRequest[A]] =
    (jsonRpcRequest: JsonRpcRequest[A]) =>
      Json.obj(
        ("jsonrpc", "2.0".asJson),
        ("id", jsonRpcRequest.id.asJson),
        ("method", jsonRpcRequest.method.asJson),
        ("params", encoder(jsonRpcRequest.params))
    )

  implicit def decodeJsonRpcRequest[A](implicit decoder: Decoder[A]): Decoder[JsonRpcRequest[A]] = (c: HCursor) => {
    for {
      id     <- c.downField("id").as[String]
      method <- c.downField("method").as[String]
      params <- c.downField("params").as[A]
    } yield {
      JsonRpcRequest(id, method, params)
    }
  }

  // JSONRPCNotification
  implicit def encodeJsonRpcNotification[A](implicit encoder: Encoder[A]): Encoder[JsonRpcNotification[A]] =
    (jsonRpcNotification: JsonRpcNotification[A]) =>
      Json.obj(
        ("jsonrpc", "2.0".asJson),
        ("method", jsonRpcNotification.method.asJson),
        ("params", encoder(jsonRpcNotification.params))
    )

  implicit def decodeJsonRpcNotification[A](implicit decoder: Decoder[A]): Decoder[JsonRpcNotification[A]] =
    (c: HCursor) => {
      for {
        method <- c.downField("method").as[String]
        params <- c.downField("params").as[A]
      } yield {
        JsonRpcNotification(method, params)
      }
    }

  // JSONRPCResultResponse
  implicit def encodeJsonRpcResultResponse[A](implicit encoder: Encoder[A]): Encoder[JsonRpcResultResponse[A]] =
    (jsonRpcResultResponse: JsonRpcResultResponse[A]) =>
      Json.obj(
        ("jsonrpc", "2.0".asJson),
        ("id", jsonRpcResultResponse.id.asJson),
        ("result", encoder(jsonRpcResultResponse.result))
    )

  implicit def decodeJsonRpcResultResponse[A](implicit decoder: Decoder[A]): Decoder[JsonRpcResultResponse[A]] =
    (c: HCursor) => {
      for {
        id     <- c.downField("id").as[String]
        result <- c.downField("result").as[A]
      } yield {
        JsonRpcResultResponse(id, result)
      }
    }

  // JSONRPCError
  implicit def encodeJsonRpcError[A](implicit encoder: Encoder[A]): Encoder[JsonRpcError[A]] =
    (jsonRpcError: JsonRpcError[A]) =>
      Json.obj(
        ("code", jsonRpcError.code.asJson),
        ("message", jsonRpcError.message.asJson),
        ("data", encodeOption[A].apply(jsonRpcError.data))
    )

  implicit def decodeJsonRpcError[A](implicit decoder: Decoder[A]): Decoder[JsonRpcError[A]] = (c: HCursor) => {
    for {
      code    <- c.downField("code").as[Int]
      message <- c.downField("message").as[String]
      data    <- c.downField("data").as[Option[A]]
    } yield {
      JsonRpcError(code, message, data)
    }
  }

  // JSONRPCErrorResponse
  implicit def encodeJsonRpcErrorResponse[A](implicit encoder: Encoder[A]): Encoder[JsonRpcErrorResponse[A]] =
    (jsonRpcErrorResponse: JsonRpcErrorResponse[A]) =>
      Json.obj(
        ("jsonrpc", "2.0".asJson),
        ("id", jsonRpcErrorResponse.id.asJson),
        ("error", encodeJsonRpcError[A].apply(jsonRpcErrorResponse.error))
    )

  implicit def decodeJsonRpcErrorResponse[A](implicit decoder: Decoder[A]): Decoder[JsonRpcErrorResponse[A]] =
    (c: HCursor) => {
      for {
        id    <- c.downField("id").as[String]
        error <- c.downField("error").as[JsonRpcError[A]]
      } yield {
        JsonRpcErrorResponse(id, error)
      }
    }
}
