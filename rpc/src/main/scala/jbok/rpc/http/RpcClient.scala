package jbok.rpc.http

import cats.effect.Effect
import cats.implicits._
import fs2.async.mutable.Topic
import io.circe.Json
import jbok.rpc.json.{JsonrpcMsg, JsonrpcRequest, RequestId}
import monix.execution.atomic._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, EntityEncoder, Request, Uri}

import scala.concurrent.ExecutionContext

final case class RpcClient[F[_]: Effect](
    uri: Uri,
    httpClient: Client[F],
    nextId: Atomic[Int],
    events: Topic[F, Option[JsonrpcMsg]]
) extends Http4sDsl[F]
    with Http4sClientDsl[F] {
  implicit val encoder: EntityEncoder[F, JsonrpcMsg] = jsonEncoderOf[F, JsonrpcMsg]
  implicit val decoder: EntityDecoder[F, JsonrpcMsg] = jsonOf[F, JsonrpcMsg]

  def call(method: String, params: Option[Json] = None): F[JsonrpcMsg] =
    send(newMessage(method, params))

  def send(msg: JsonrpcMsg): F[JsonrpcMsg] = {
    for {
      req <- msgToRequest(uri, msg)
      resp <- httpClient.expect[JsonrpcMsg](req)
      _ <- events.publish1(resp.some)
    } yield resp
  }

  def newMessage(method: String, params: Option[Json] = None, id: Option[RequestId] = None): JsonrpcMsg =
    JsonrpcRequest(method, id.getOrElse(RequestId(nextId.getAndTransform(_ + 1))), params)

  def msgToRequest(uri: Uri, msg: JsonrpcMsg): F[Request[F]] = {
    POST.apply(uri, msg)
  }
}

object RpcClient {
  def apply[F[_]: Effect](uri: Uri)(implicit ec: ExecutionContext): F[RpcClient[F]] = {
    for {
      client <- Http1Client[F]()
      topic <- fs2.async.topic[F, Option[JsonrpcMsg]](None)
    } yield RpcClient(uri, client, Atomic(0), topic)
  }
}
