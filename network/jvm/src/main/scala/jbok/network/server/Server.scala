package jbok.network.server
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.common.TcpUtil
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import scodec.Codec
import scodec.bits.BitVector
import cats.effect.implicits._

trait Server[F[_]] {
  def localAddress: InetSocketAddress

  def stream: Stream[F, Unit]

  def start: F[Unit]

  def stop: F[Unit]
}

object Server {
  def tcp[F[_], A: Codec](bind: InetSocketAddress, pipe: Pipe[F, A, A], maxOpen: Int = Int.MaxValue)(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup): F[Server[F]] =
    for {
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield
      new Server[F] {
        private[this] val log = org.log4s.getLogger

        override val localAddress: InetSocketAddress = bind

        override val stream: Stream[F, Unit] =
          fs2.io.tcp
            .serverWithLocalAddress[F](bind)
            .map {
              case Left(bindAddr) =>
                log.info(s"server bound to ${bindAddr}")
                Stream.empty.covary[F]

              case Right(s) =>
                Stream
                  .resource(s)
                  .flatMap(socket => {
                    for {
                      conn <- Stream.eval(TcpUtil.socketToConnection[F](socket, true))
                      _ <- conn
                        .reads[A]()
                        .through(pipe)
                        .to(conn.writes[A]())
                    } yield ()
                  })
            }
            .parJoin(maxOpen)
            .handleErrorWith(e => Stream.eval[F, Unit](F.delay(log.error(e)(s"server error: ${e}"))))

        override def start: F[Unit] =
          haltWhenTrue.get.flatMap {
            case false => F.unit
            case true  => haltWhenTrue.set(false) *> stream.interruptWhen(haltWhenTrue).compile.drain.start.void
          }

        override def stop: F[Unit] =
          haltWhenTrue.set(true)
      }

  def websocket[F[_], A: Codec](bind: InetSocketAddress, pipe: Pipe[F, A, A], maxOpen: Int = Int.MaxValue)(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup): F[Server[F]] =
    for {
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield
      new Server[F] {
        private[this] val log = org.log4s.getLogger

        override val localAddress: InetSocketAddress = bind

        override val stream: Stream[F, Unit] = {
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

          val builder = BlazeBuilder[F]
            .bindSocketAddress(bind)
            .mountService(service, "/")
            .withWebSockets(true)
            .withoutBanner
            .withNio2(true)

          log.info(s"binding on ${bind}")
          builder.serve.handleErrorWith(e => Stream.eval(F.delay(log.error(e)("onError")))).drain
        }

        override def start: F[Unit] =
          haltWhenTrue.get.flatMap {
            case false => F.unit
            case true  => haltWhenTrue.set(false) *> stream.interruptWhen(haltWhenTrue).compile.drain.start.void
          }

        override def stop: F[Unit] =
          haltWhenTrue.set(true)
      }
}
