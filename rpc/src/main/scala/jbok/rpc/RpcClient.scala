package jbok.rpc

import cats.effect.Effect
import cats.implicits._
import fs2.async.mutable.Topic
import io.circe.Json
import jbok.rpc.json.{JsonrpcMsg, JsonrpcRequest, JsonrpcResponse, RequestId}
import monix.execution.atomic._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, EntityEncoder, Uri}

import scala.concurrent.ExecutionContext

final case class RpcClient[F[_]: Effect](
    uri: Uri,
    httpClient: Client[F],
    nextId: Atomic[Int],
    events: Topic[F, JsonrpcMsg]
) extends Http4sDsl[F]
    with Http4sClientDsl[F] {
  implicit val encoder: EntityEncoder[F, JsonrpcMsg] = jsonEncoderOf[F, JsonrpcMsg]
  implicit val decoder: EntityDecoder[F, JsonrpcMsg] = jsonOf[F, JsonrpcMsg]

  def call(method: String, params: Option[Json] = None): F[JsonrpcMsg] =
    send(newMessage(method, params))

  def send(msg: JsonrpcMsg): F[JsonrpcMsg] = {
    for {
      req <- POST.apply(uri, msg)
      resp <- httpClient.expect[JsonrpcMsg](req)
      _ <- events.publish1(resp)
    } yield resp
  }

  def newMessage(method: String, params: Option[Json] = None, id: Option[RequestId] = None): JsonrpcMsg =
    JsonrpcRequest(method, params, id.getOrElse(RequestId(nextId.getAndTransform(_ + 1))))
}

object RpcClient {
  def apply[F[_]: Effect](uri: Uri)(implicit ec: ExecutionContext): F[RpcClient[F]] = {
    for {
      client <- Http1Client[F]()
      topic <- fs2.async.topic[F, JsonrpcMsg](JsonrpcResponse.empty)
    } yield RpcClient(uri, client, Atomic(0), topic)
  }
}
