package jbok.rpc

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.charset.StandardCharsets

import cats.effect.Effect
import cats.implicits._
import io.circe.Json
import fs2._
import fs2.async.mutable.{Queue, Signal, Topic}
import fs2.async.{Promise, Ref}
import jbok.codec._
import jbok.rpc.json._
import monix.execution.atomic.Atomic
import spinoco.fs2.http.websocket.{Frame, WebSocket, WebSocketRequest}
import spinoco.protocol.http.HttpResponseHeader
import spinoco.protocol.http.Uri.QueryParameter

import scala.concurrent.ExecutionContext

abstract class RpcClient[F[_]](val addr: InetSocketAddress)(
    implicit F: Effect[F],
    AG: AsynchronousChannelGroup,
    EC: ExecutionContext,
    S: Scheduler
) {
  val notification: Topic[F, Option[JsonrpcNotification]]

  val nextId: Atomic[Int] = Atomic(0)

  implicit val textCodec = scodec.codecs.string(StandardCharsets.UTF_8)

  val promises: Ref[F, Map[RequestId, Promise[F, JsonrpcResponse]]]

  val queue: Queue[F, JsonrpcMsg]

  val pipe: Pipe[F, Frame[String], Frame[String]] = { frame =>
    val inbound = frame
      .map(x => x.a.decodeJson[JsonrpcMsg].right.get)
      .evalMap {
        case x: JsonrpcResponse =>
          for {
            m <- promises.get
            _ <- m.apply(x.id).complete(x)
          } yield ()

        case x: JsonrpcNotification =>
          notification.publish1(x.some)

        case _ => ???
      }

    val outbound = queue.dequeue.map(x => {
      Frame.Text(x.asJson.noSpaces)
    })

    outbound.concurrently(inbound)
  }

  val deadSignal: Signal[F, Boolean]

  val clientRef: Ref[F, Boolean]

  def isUp: F[Boolean] = clientRef.get.map(_ == true)

  def start: Stream[F, Option[HttpResponseHeader]] = {
    val request =
      WebSocketRequest.ws(addr.getHostString, addr.getPort, "/", QueryParameter.single("encoding", "text"))
    Stream.bracket[F, Unit, Option[HttpResponseHeader]](clientRef.setSync(true))(
      _ => WebSocket.client(request, pipe).interruptWhen(deadSignal),
      _ => clientRef.setSync(false)
    )
  }

  def stop: F[Unit] =
    deadSignal.set(true)

  def subscribe: Stream[F, JsonrpcNotification] = {
    notification
      .subscribe(1)
      .unNone
  }

  def call(method: String, params: Option[Json] = None): F[JsonrpcResponse] =
    send(newRequest(method, params))

  def send(req: JsonrpcRequest): F[JsonrpcResponse] = {
    for {
      promise <- fs2.async.promise[F, JsonrpcResponse]
      _ <- promises.modify(_ + (req.id -> promise))
      _ <- queue.enqueue1(req)
      resp <- promise.get
      _ <- promises.modify(_ - req.id)
    } yield resp
  }

  def newRequest(method: String, params: Option[Json] = None, id: Option[RequestId] = None): JsonrpcRequest =
    JsonrpcRequest(method, id.getOrElse(RequestId(nextId.getAndTransform(_ + 1))), params)
}

object RpcClient {
  def apply[F[_]: Effect](addr: InetSocketAddress)(
      implicit AG: AsynchronousChannelGroup,
      ec: ExecutionContext,
      S: Scheduler): F[RpcClient[F]] =
    for {
      q <- fs2.async.unboundedQueue[F, JsonrpcMsg]
      topic <- fs2.async.topic[F, Option[JsonrpcNotification]](None)
      signal <- fs2.async.signalOf[F, Boolean](false)
      promises_ <- fs2.async.refOf(Map.empty[RequestId, Promise[F, JsonrpcResponse]])
      ref <- fs2.async.refOf[F, Boolean](false)
    } yield {
      new RpcClient[F](addr) {
        override val queue = q
        override val notification = topic
        override val deadSignal = signal
        override val promises = promises_
        override val clientRef = ref
      }
    }
}
