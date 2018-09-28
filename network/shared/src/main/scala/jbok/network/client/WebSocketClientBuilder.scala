package jbok.network.client

import java.net.URI

import cats.effect.ConcurrentEffect
import fs2.Pipe
import jbok.network.execution._
import scodec.Codec
import spinoco.fs2.http.websocket.{Frame, WebSocketRequest}

class WebSocketClientBuilder[F[_]: ConcurrentEffect, A: Codec] extends ClientBuilder[F, A] {

  private[this] val log = org.log4s.getLogger

  override def connect(to: URI,
                       pipe: Pipe[F, A, A],
                       reuseAddress: Boolean,
                       sendBufferSize: Int,
                       receiveBufferSize: Int,
                       keepAlive: Boolean,
                       noDelay: Boolean): fs2.Stream[F, Unit] = {
    val request: WebSocketRequest = WebSocketRequest.ws(to.getHost, to.getPort, "/")

    log.debug(s"sending request: ${request}")

    val framePipe: Pipe[F, Frame[A], Frame[A]] = { input =>
      input.map(_.a).through(pipe).map(a => Frame.Binary(a))
    }

    spinoco.fs2.http.websocket.WebSocket.client[F, A, A](request, framePipe).drain
  }
}

object WebSocketClientBuilder {
  def apply[F[_]: ConcurrentEffect, A: Codec] = new WebSocketClientBuilder[F, A]
}
