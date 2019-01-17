package jbok.network.client

import java.net.URI
import java.util.UUID

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.facade.{ErrorEvent, Event, MessageEvent, WebSocket}
import jbok.network.{Connection, Message, Request, Response}
import scodec.bits.BitVector

import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

object WsClient {
  def apply[F[_]](
      uri: URI,
      maxQueued: Int = 64,
      maxBytes: Int = 256 * 1024
  )(implicit F: ConcurrentEffect[F], CS: ContextShift[F]): F[Client[F]] =
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
              case req: Request[F] => F.pure(Some(req))
              case res: Response[F] =>
                promises.get.flatMap(_.get(res.id) match {
                  case Some(p) => p.complete(res).as(None)
                  case None    => F.pure(None)
                })
            }
            .unNone to in.enqueue
        )
      }

      val url = s"ws://${uri.getHost}:${uri.getPort}"

      println(s"connecting to ${url}")

      val resource: Resource[F, WebSocket] = Resource.make {
        for {
          ws <- F.delay(new WebSocket(url))
          _ = ws.binaryType = "arraybuffer" // so we can cast blob as arrayBuffer
          opened <- F.async[WebSocket] { cb =>
            ws.onopen = { _: Event =>
              cb(Right(ws))
            }

            ws.onerror = { event: ErrorEvent =>
              cb(Left(new Exception(event.toString)))
            }
          }
          _ = println("connection established")
        } yield opened
      } { socket =>
        F.delay(socket.close())
      }

      val stream = Stream.resource(resource).flatMap { ws =>
        for {
          queue <- Stream.eval(Queue.unbounded[F, Message[F]])
          _ = ws.onmessage = { event: MessageEvent =>
            val arr  = event.data.asInstanceOf[ArrayBuffer]
            val bits = BitVector(TypedArrayBuffer.wrap(arr))
            F.runAsync(Message.decodeBytes[F](bits.bytes).flatMap(message => queue.enqueue1(message)))(_ => IO.unit)
              .unsafeRunSync()
          }
          a   <- queue.dequeue.through(pipe)
          str <- Stream.eval(a.encodeBytes.map(_.toBase64))
          _   <- Stream.eval(Sync[F].delay(ws.send(str)))
        } yield ()
      }

      Connection[F](stream, in, out, promises, false, haltWhenTrue)
    }
}
