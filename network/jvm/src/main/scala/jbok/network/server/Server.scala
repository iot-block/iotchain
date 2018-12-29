package jbok.network.server

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.implicits._
import fs2._
import fs2.concurrent.Queue
import jbok.common.metrics.Metrics
import jbok.network.common.{RequestId, TcpUtil}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.metrics.prometheus.PrometheusExportService
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import scodec.Codec
import scodec.bits.BitVector
import _root_.io.prometheus.client.CollectorRegistry

import scala.concurrent.duration._

final case class Server[F[_]](bind: InetSocketAddress, stream: Stream[F, Unit])

object Server {
  def tcp[F[_], A: Codec: RequestId](bind: InetSocketAddress, pipe: Pipe[F, A, A], maxOpen: Int = Int.MaxValue)(
      implicit F: ConcurrentEffect[F],
      CS: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup): Server[F] = {
    val stream = fs2.io.tcp.Socket
      .server[F](bind)
      .map(s =>
        for {
          conn <- Stream.eval(TcpUtil.socketToConnection[F, A](s, true))
          _    <- Stream.eval(conn.start)
          _    <- conn.reads.through(pipe).to(conn.sink)
        } yield ())
      .parJoin(maxOpen)

    Server(bind, stream)
  }

  def websocket[F[_], A: Codec](
      bind: InetSocketAddress,
      pipe: Pipe[F, A, A],
      metrics: Metrics[F],
      maxOpen: Int = Int.MaxValue
  )(
      implicit F: ConcurrentEffect[F],
      T: Timer[F]
  ): Server[F] = {
    val log = jbok.common.log.getLogger("WebSocket")

    val registry: Option[CollectorRegistry] = metrics.registry match {
      case x: CollectorRegistry => Some(x)
      case _                    => None
    }

    val registerDefaults =
      registry.fold(Stream.empty.covaryAll[F, Unit])(r =>
        Stream.eval[F, Unit](PrometheusExportService.addDefaults[F](r)))

    val stream
      : Stream[F, Unit] = registerDefaults ++ Stream.eval_(F.delay(log.info(s"successfully bound to ${bind}"))) ++ {
      val dsl = Http4sDsl[F]
      import dsl._
      val service = HttpRoutes.of[F] {
        case GET -> Root =>
          Queue.unbounded[F, A].flatMap { queue =>
            val toClient = queue.dequeue.map { x =>
              val bytes = Codec[A].encode(x).require.bytes
              WebSocketFrame.Binary(bytes, true)
            }

            val fromClient: Sink[F, WebSocketFrame] = { s: Stream[F, WebSocketFrame] =>
              s.map {
                  case WebSocketFrame.Binary(bytes, _) =>
                    Codec[A].decode(bytes.bits).require.value

                  case WebSocketFrame.Text(text, _) =>
                    Codec[A].decode(BitVector.fromValidBase64(text)).require.value
                }
                .through(pipe)
                .to(queue.enqueue)
            }

            WebSocketBuilder[F].build(toClient, fromClient)
          }
      }

      val httpApp = registry match {
        case Some(r) => Router("/" -> service, "/" -> PrometheusExportService(r).routes).orNotFound
        case None    => Router("/" -> service).orNotFound
      }

      val builder = BlazeServerBuilder[F]
        .bindSocketAddress(bind)
        .withHttpApp(httpApp)
        .withWebSockets(true)
        .withoutBanner
        .withIdleTimeout(60.seconds)

      builder.serve.drain
        .onFinalize(F.delay(log.info(s"stop listening to ${bind}")))
    }

    Server(bind, stream)
  }
}
