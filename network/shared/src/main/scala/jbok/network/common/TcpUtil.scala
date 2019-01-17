package jbok.network.common

import java.util.UUID

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import fs2.io.tcp.Socket
import jbok.network.{Connection, Message, Request, Response}

private[jbok] object TcpUtil {
  def socketToConnection[F[_]](
      resource: Resource[F, Socket[F]],
      incoming: Boolean,
      maxBytes: Int = 256 * 1024,
      maxQueued: Int = 64
  )(implicit F: ConcurrentEffect[F], CS: ContextShift[F], T: Timer[F]): F[Connection[F]] =
    for {
      in           <- Queue.bounded[F, Message[F]](maxQueued)
      out          <- Queue.bounded[F, Message[F]](maxQueued)
      promises     <- Ref.of[F, Map[UUID, Deferred[F, Response[F]]]](Map.empty)
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield {
      val sink: Sink[F, Message[F]] = { input =>
        input
          .evalMap[F, Unit] {
            case req: Request[F] =>
              in.enqueue1(req)
            case res: Response[F] =>
              promises.get.flatMap(_.get(res.id) match {
                case Some(p) => p.complete(res)
                case None    => in.enqueue1(res)
              })
          }
      }

      val stream =
        Stream.eval(haltWhenTrue.set(false)) >>
          Stream
            .resource(resource)
            .flatMap { socket =>
              out.dequeue through Message.encodePipe[F] to socket.writes(None) mergeHaltBoth {
                socket.reads(maxBytes, None) through Message.decodePipe[F] to sink
              }
            }
            .onFinalize(haltWhenTrue.set(true))
            .interruptWhen(haltWhenTrue)

      Connection(stream, in, out, promises, incoming, haltWhenTrue)
    }
}
