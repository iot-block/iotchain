package jbok.network.server

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import fs2._
import fs2.async.Ref
import jbok.network.Connection
import org.http4s.HttpService
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits
import org.http4s.websocket.WebsocketBits._
import scodec.Codec
import cats.implicits._
import scodec.bits.BitVector
import jbok.network.execution._

class WSServerBuilder[F[_], A: Codec](implicit F: ConcurrentEffect[F]) extends ServerBuilder[F, A] with Http4sDsl[F] {

  private[this] val log = org.log4s.getLogger

  override def listen(
      bind: InetSocketAddress,
      pipe: Pipe[F, A, A],
      conns: Ref[F, Map[InetSocketAddress, Connection[F, A]]],
      maxConcurrent: Int,
      maxQueued: Int,
      reuseAddress: Boolean,
      receiveBufferSize: Int
  ): fs2.Stream[F, Unit] = {

    val service = HttpService[F] {
      case GET -> Root =>
        fs2.async.unboundedQueue[F, A].flatMap { queue =>
          val toClient = queue.dequeue.map { x =>
            val bytes = Codec[A].encode(x).require.toByteArray
            WebsocketBits.Binary(bytes, true)
          }

          val fromClient: Sink[F, WebSocketFrame] = { s: Stream[F, WebSocketFrame] =>
            s.map {
                case WebsocketBits.Binary(bytes, _) =>
                  Codec[A].decode(BitVector(bytes)).require.value

                case WebsocketBits.Text(text, _) =>
                  Codec[A].decode(BitVector.fromValidBase64(text)).require.value
              }
              .through(pipe)
              .to(queue.enqueue)
          }

          WebSocketBuilder[F].build(toClient, fromClient)
        }
    }

    val builder = BlazeBuilder[F]
      .bindSocketAddress(bind)
      .mountService(service, "/")
      .withWebSockets(true)
      .withoutBanner
      .withNio2(true)

    log.info(s"binding on ${bind}")
    builder.serve.drain
      .handleErrorWith(e => Stream.eval(F.delay(log.error(e)("onError"))))
      .onFinalize(F.delay(log.info(s"onFinalize")))
  }
}

object WSServerBuilder {
  def apply[F[_]: ConcurrentEffect, A: Codec]: WSServerBuilder[F, A] = new WSServerBuilder[F, A]
}
