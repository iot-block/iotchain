package jbok.network.rpc

import java.util.UUID

import io.circe._
import io.circe.syntax._
import io.circe.Decoder.decodeOption
import io.circe.Encoder.encodeOption

object jsonrpc {
  final case class RpcRequest[A](method: String, params: A, id: String = UUID.randomUUID().toString)
  final case class RpcNotification[A](method: String, params: A)
  final case class RpcResultResponse[A](id: String, result: A)
  final case class RpcErrorResponse[+A](id: String, error: RpcError[A])
  final case class RpcError[+A](code: Int, message: String, data: Option[A]) extends Exception(message)

  object RpcErrors {
    val parseError =
      RpcError(
        -32700,
        "Parse error",
        Some("Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text."))
    val invalidRequest =
      RpcError(-32600, "Invalid Request", Some("The JSON sent is not a valid Request object."))
    def methodNotFound(method: String) =
      RpcError(-32601, "Method not found", Some(s"The method ${method} does not exist / is not available."))
    val invalidParams =
      RpcError(-32602, "Invalid params", Some("Invalid method parameter(s)."))
    val internalError =
      RpcError(-32603, "Internal error", Some("Internal JSON-RPC error."))
  }

  // JSONRPCRequest
  implicit def encodeJsonRpcRequest[A](implicit encoder: Encoder[A]): Encoder[RpcRequest[A]] =
    (rpcRequest: RpcRequest[A]) =>
      Json.obj(
        ("jsonrpc", "2.0".asJson),
        ("id", rpcRequest.id.asJson),
        ("method", rpcRequest.method.asJson),
        ("params", encoder(rpcRequest.params))
    )

  implicit def decodeJsonRpcRequest[A](implicit decoder: Decoder[A]): Decoder[RpcRequest[A]] = (c: HCursor) => {
    for {
      id     <- c.downField("id").as[String]
      method <- c.downField("method").as[String]
      params <- c.downField("params").as[A]
    } yield {
      RpcRequest(method, params, id)
    }
  }

  // JSONRPCNotification
  implicit def encodeJsonRpcNotification[A](implicit encoder: Encoder[A]): Encoder[RpcNotification[A]] =
    (RpcNotification: RpcNotification[A]) =>
      Json.obj(
        ("jsonrpc", "2.0".asJson),
        ("method", RpcNotification.method.asJson),
        ("params", encoder(RpcNotification.params))
    )

  implicit def decodeJsonRpcNotification[A](implicit decoder: Decoder[A]): Decoder[RpcNotification[A]] =
    (c: HCursor) => {
      for {
        method <- c.downField("method").as[String]
        params <- c.downField("params").as[A]
      } yield {
        RpcNotification(method, params)
      }
    }

  // JSONRPCResultResponse
  implicit def encodeJsonRpcResultResponse[A](implicit encoder: Encoder[A]): Encoder[RpcResultResponse[A]] =
    (rpcResultResponse: RpcResultResponse[A]) =>
      Json.obj(
        ("jsonrpc", "2.0".asJson),
        ("id", rpcResultResponse.id.asJson),
        ("result", encoder(rpcResultResponse.result))
    )

  implicit def decodeJsonRpcResultResponse[A](implicit decoder: Decoder[A]): Decoder[RpcResultResponse[A]] =
    (c: HCursor) => {
      for {
        id     <- c.downField("id").as[String]
        result <- c.downField("result").as[A]
      } yield {
        RpcResultResponse(id, result)
      }
    }

  // JSONRPCError
  implicit def encodeJsonRpcError[A](implicit encoder: Encoder[A]): Encoder[RpcError[A]] =
    (rpcError: RpcError[A]) =>
      Json.obj(
        ("code", rpcError.code.asJson),
        ("message", rpcError.message.asJson),
        ("data", encodeOption[A].apply(rpcError.data))
    )

  implicit def decodeJsonRpcError[A](implicit decoder: Decoder[A]): Decoder[RpcError[A]] = (c: HCursor) => {
    for {
      code    <- c.downField("code").as[Int]
      message <- c.downField("message").as[String]
      data    <- c.downField("data").as[Option[A]]
    } yield {
      RpcError(code, message, data)
    }
  }

  // JSONRPCErrorResponse
  implicit def encodeJsonRpcErrorResponse[A](implicit encoder: Encoder[A]): Encoder[RpcErrorResponse[A]] =
    (rpcErrorResponse: RpcErrorResponse[A]) =>
      Json.obj(
        ("jsonrpc", "2.0".asJson),
        ("id", rpcErrorResponse.id.asJson),
        ("error", encodeJsonRpcError[A].apply(rpcErrorResponse.error))
    )

  implicit def decodeJsonRpcErrorResponse[A](implicit decoder: Decoder[A]): Decoder[RpcErrorResponse[A]] =
    (c: HCursor) => {
      for {
        id    <- c.downField("id").as[String]
        error <- c.downField("error").as[RpcError[A]]
      } yield {
        RpcErrorResponse(id, error)
      }
    }
}
