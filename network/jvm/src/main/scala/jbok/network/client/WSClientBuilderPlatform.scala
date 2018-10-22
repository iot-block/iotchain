package jbok.network.client

import java.net.URI

import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import fs2._
import jbok.common.execution._
import scodec.Codec
import spinoco.fs2.http.websocket.{Frame, WebSocketRequest}

class WSClientBuilderPlatform[F[_]: ConcurrentEffect, A: Codec](implicit CS: ContextShift[F], T: Timer[F])
    extends ClientBuilder[F, A] {

  override def connect(to: URI,
                       pipe: Pipe[F, A, A],
                       reuseAddress: Boolean,
                       sendBufferSize: Int,
                       receiveBufferSize: Int,
                       keepAlive: Boolean,
                       noDelay: Boolean): Stream[F, Unit] = {
    val request: WebSocketRequest = WebSocketRequest.ws(to.getHost, to.getPort, "/")

    println(s"sending request: ${request}")

    val framePipe: Pipe[F, Frame[A], Frame[A]] = { input =>
      input.map(_.a).through(pipe).map(a => Frame.Binary(a))
    }

    spinoco.fs2.http.websocket.WebSocket.client[F, A, A](request, framePipe).drain
  }
}

object WSClientBuilderPlatform {
  def apply[F[_]: ConcurrentEffect, A: Codec](implicit CS: ContextShift[F], T: Timer[F]) =
    new WSClientBuilderPlatform[F, A]
}
