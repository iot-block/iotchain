package jbok.network.client

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import fs2.Pipe
import scodec.Codec
import spinoco.fs2.http.websocket.{Frame, WebSocketRequest}
import jbok.network.execution._

class WebSocketClientBuilder[F[_]: ConcurrentEffect, A: Codec] extends ClientBuilder[F, A] {

  override def connect(to: InetSocketAddress,
                       pipe: Pipe[F, A, A],
                       reuseAddress: Boolean,
                       sendBufferSize: Int,
                       receiveBufferSize: Int,
                       keepAlive: Boolean,
                       noDelay: Boolean): fs2.Stream[F, Unit] = {
    val request: WebSocketRequest = WebSocketRequest.ws(to.getHostName, to.getPort, "/")

    val framePipe: Pipe[F, Frame[A], Frame[A]] = { input =>
      input.map(_.a).through(pipe).map(a => Frame.Binary(a))
    }

    spinoco.fs2.http.websocket.WebSocket.client[F, A, A](request, framePipe).drain
  }
}

object WebSocketClientBuilder {
  def apply[F[_]: ConcurrentEffect, A: Codec] = new WebSocketClientBuilder[F, A]
}
