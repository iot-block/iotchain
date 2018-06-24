package jbok.rpc.transport

import java.nio.channels.AsynchronousChannelGroup
import java.nio.charset.StandardCharsets

import cats.effect.{ConcurrentEffect, Effect}
import cats.implicits._
import fs2.async.mutable.{Signal, Topic}
import fs2.async.{Promise, Ref}
import io.circe
import io.circe.Json
import fs2._
import jbok.rpc.HostPort

import scala.concurrent.ExecutionContext

abstract class WSTransport[F[_]](addr: HostPort)(implicit F: Effect[F], ec: ExecutionContext)
    extends DuplexTransport[F, String, String](addr)

object WSTransport {
  import spinoco.fs2.http.websocket.{Frame, WebSocket, WebSocketRequest}
  import spinoco.protocol.http.Uri.QueryParameter

  def apply[F[_]](addr: HostPort)(
      implicit F: ConcurrentEffect[F],
      AG: AsynchronousChannelGroup,
      S: Scheduler,
      EC: ExecutionContext): F[WSTransport[F]] = {
    for {
      _events <- fs2.async.topic[F, Option[String]](None)
      _promises <- fs2.async.refOf[F, Map[String, Promise[F, String]]](Map.empty)
      _stopWhenTrue <- fs2.async.signalOf[F, Boolean](true)
      queue <- fs2.async.unboundedQueue[F, String]
    } yield
      new WSTransport[F](addr) {
        override val events: Topic[F, Option[String]] = _events

        override val promises: Ref[F, Map[String, Promise[F, String]]] = _promises

        override val stopWhenTrue: Signal[F, Boolean] = _stopWhenTrue

        override def parse(x: String): F[(Option[String], String)] =
          (circe.parser.parse(x).getOrElse(Json.Null).hcursor.get[String]("id") match {
            case Left(e) => (None, x)
            case Right(id) => (Some(id), x)
          }).pure[F]

        val pipe: Pipe[F, Frame[String], Frame[String]] = { frame =>
          val inbound: Stream[F, Unit] =
            frame
              .evalMap {
                case Frame.Text(x) => handle(x)
                case Frame.Binary(_) => ???
              }

          val outbound = queue.dequeue.map(x => Frame.Text(x))

          outbound.concurrently(inbound)
        }

        override def send(req: String): F[Unit] = queue.enqueue1(req)

        override def start: F[Unit] = {
          implicit val codec: scodec.Codec[String] = scodec.codecs.string(StandardCharsets.UTF_8)

          val request = addr.port match {
            case Some(p) => WebSocketRequest.ws(addr.host, p, "/", QueryParameter.single("encoding", "text"))
            case None => WebSocketRequest.ws(addr.host, "/", QueryParameter.single("encoding", "text"))
          }

          val stream = WebSocket.client(request, pipe).interruptWhen(stopWhenTrue)

          F.start(stream.compile.drain) *> stopWhenTrue.set(false)
        }

        override def stop: F[Unit] = stopWhenTrue.set(true)
      }
  }
}
