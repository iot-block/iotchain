package jbok.network.client

import java.net.URI
import java.nio.channels.AsynchronousChannelGroup

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.Connection
import jbok.network.common.RequestId
import scodec.Codec
import spinoco.fs2.http.websocket.{Frame, WebSocketRequest}

object WsClient {

  def apply[F[_], A: Codec: RequestId](
      uri: URI,
      maxQueued: Int = 64,
      maxBytes: Int = 256 * 1024
  )(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      CS: ContextShift[F],
      AG: AsynchronousChannelGroup
  ): F[Connection[F, A]] =
    for {
      in           <- Queue.bounded[F, A](maxQueued)
      out          <- Queue.bounded[F, A](maxQueued)
      promises     <- Ref.of[F, Map[String, Deferred[F, A]]](Map.empty)
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield {
      val pipe: Pipe[F, A, A] = { input =>
        out.dequeue.concurrently(
          input
            .evalMap[F, Option[A]] { a =>
              RequestId[A].id(a) match {
                case "" => a.some.pure[F]
                case id =>
                  promises.get.flatMap(_.get(id) match {
                    case Some(p) => p.complete(a).as(None)
                    case None    => a.some.pure[F]
                  })
              }
            }
            .unNone to in.enqueue)
      }

      val request: WebSocketRequest = WebSocketRequest.ws(uri.getHost, uri.getPort, "/")

      val framePipe: Pipe[F, Frame[A], Frame[A]] = { input =>
        input.map(_.a).through(pipe).map(a => Frame.Binary(a))
      }

      val stream = spinoco.fs2.http.websocket.WebSocket.client[F, A, A](request, framePipe).drain

      Client[F, A](stream, in, out, promises, uri, haltWhenTrue)
    }
}

object WsClientForNode {
  def apply[F[_], A: Codec: RequestId](
      uri: URI,
      maxQueued: Int = 64,
      maxBytes: Int = 256 * 1024
  )(implicit F: ConcurrentEffect[F],
    T: Timer[F],
    CS: ContextShift[F],
    AG: AsynchronousChannelGroup): F[Connection[F, A]] = WsClient(uri, maxQueued, maxBytes)
}
