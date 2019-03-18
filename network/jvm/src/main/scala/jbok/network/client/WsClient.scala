package jbok.network.client

import java.net.URI
import java.nio.channels.AsynchronousChannelGroup
import java.util.UUID

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.{Connection, Message, Request, Response}
import spinoco.fs2.http.websocket.{Frame, WebSocketRequest}

object WsClient {
  private[this] val log = jbok.common.log.getLogger("WsClient")
  def apply[F[_]](
      uri: URI,
      maxQueued: Int = 64,
      maxBytes: Int = 256 * 1024
  )(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      CS: ContextShift[F],
      AG: AsynchronousChannelGroup
  ): F[Client[F]] =
    for {
      in           <- Queue.bounded[F, Message[F]](maxQueued)
      out          <- Queue.bounded[F, Message[F]](maxQueued)
      promises     <- Ref.of[F, Map[UUID, Deferred[F, Response[F]]]](Map.empty)
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield {
      val pipe: Pipe[F, Message[F], Message[F]] = { input =>
        out.dequeue.concurrently(
          input
            .evalMap[F, Option[Message[F]]] {
              case req: Request[F] =>
                log.debug(s"received ${req} from server")
                F.pure(Some(req))
              case res: Response[F] =>
                log.debug(s"received ${res} from server")
                promises.get.flatMap(_.get(res.id) match {
                  case Some(p) => p.complete(res).as(None)
                  case None    => F.pure(Some(res))
                })
            }
            .unNone to in.enqueue
        )
      }

      val request: WebSocketRequest = WebSocketRequest.ws(uri.getHost, uri.getPort, "/")

      val framePipe: Pipe[F, Frame[Message[F]], Frame[Message[F]]] = { input =>
        input.map(_.a).through(pipe).map(a => Frame.Binary(a))
      }

      val stream = spinoco.fs2.http.websocket.WebSocket.client[F, Message[F], Message[F]](
        request = request,
        pipe = framePipe,
        maxFrameSize = 4 * 1024 * 1024
      ).drain

      Connection[F](stream, in, out, promises, false, haltWhenTrue)
    }
}
