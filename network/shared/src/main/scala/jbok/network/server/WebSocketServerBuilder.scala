package jbok.network.server

import java.net.InetSocketAddress

import cats.effect.{ConcurrentEffect, Sync}
import fs2._
import fs2.async.Ref
import jbok.network.Connection
import jbok.network.execution._
import scodec.Codec
import spinoco.fs2.http.HttpResponse
import spinoco.fs2.http.websocket.Frame
import spinoco.protocol.http.{HttpRequestHeader, HttpStatusCode}

class WebSocketServerBuilder[F[_]: ConcurrentEffect, A: Codec] extends ServerBuilder[F, A] {
  private[this] val log = org.log4s.getLogger

  override def listen(bind: InetSocketAddress,
                      pipe: Pipe[F, A, A],
                      conns: Ref[F, Map[InetSocketAddress, Connection[F, A]]],
                      maxConcurrent: Int,
                      maxQueued: Int,
                      reuseAddress: Boolean,
                      receiveBufferSize: Int): fs2.Stream[F, Unit] = {

    log.info(s"listening to ${bind}")

    val framePipe: Pipe[F, Frame[A], Frame[A]] = { input =>
      input.map(x => {
        log.info(s"received: ${x.a}")
        x.a
      }).through(pipe).map(a => Frame.Binary(a))
    }
    spinoco.fs2.http
      .server[F](
        bind,
        maxConcurrent = maxConcurrent,
        requestFailure = handleRequestParseError _,
        sendFailure = handleSendFailure _
      )(spinoco.fs2.http.websocket.server[F, A, A](framePipe))
      .handleErrorWith(e => Stream.eval(Sync[F].delay(log.error(e)(s"error"))))
  }

  def handleRequestParseError(err: Throwable): Stream[F, HttpResponse[F]] =
    Stream
      .suspend {
        log.error(err)(s"request parse error")
        Stream.emit(HttpResponse[F](HttpStatusCode.BadRequest))
      }
      .covary[F]

  /** default handler for failures of sending request/response **/
  def handleSendFailure(header: Option[HttpRequestHeader],
                        response: HttpResponse[F],
                        err: Throwable): Stream[F, Nothing] =
    Stream.suspend {
      log.error(err)(s"send failure")
      Stream.empty
    }

}

object WebSocketServerBuilder {
  def apply[F[_]: ConcurrentEffect, A: Codec] = new WebSocketServerBuilder[F, A]
}
