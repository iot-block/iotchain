package jbok.network.client
import java.net.URI

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.common.RequestId
import org.scalajs.dom
import org.scalajs.dom._
import scodec.Codec
import scodec.bits.BitVector

import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

object WsClient {
  def apply[F[_], A: Codec: RequestId](
      uri: URI,
      maxQueued: Int = 64,
      maxBytes: Int = 256 * 1024
  )(implicit F: ConcurrentEffect[F], CS: ContextShift[F]): F[Client[F, A]] =
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

      val url = s"ws://${uri.getHost}:${uri.getPort}"

      println(s"connecting to ${url}")

      val resource: Resource[F, dom.WebSocket] = Resource.make {
        for {
          ws <- F.delay(new dom.WebSocket(url))
          _ = ws.binaryType = "arraybuffer" // so we can cast blob as arrayBuffer
          opened <- F.async[dom.WebSocket] { cb =>
            ws.onopen = { event: Event =>
              cb(Right(ws))
            }

            ws.onerror = { event: Event =>
              println(s"onerror: ${scala.scalajs.js.JSON.stringify(event)}")
              cb(Left(new Exception(event.toString)))
            }
          }
          _ = println("connection established")
        } yield opened
      } { socket =>
        F.delay(socket.close(0, ""))
      }

      val stream = Stream.resource(resource).flatMap { ws =>
        for {
          queue <- Stream.eval(Queue.unbounded[F, A])
          _ = ws.onmessage = { event: MessageEvent =>
            val arr  = event.data.asInstanceOf[ArrayBuffer]
            val bits = BitVector(TypedArrayBuffer.wrap(arr))
            val a    = Codec[A].decode(bits).require.value
            F.runAsync(queue.enqueue1(a))(_ => IO.unit).unsafeRunSync()
          }
          a <- queue.dequeue.through(pipe)
          str = Codec[A].encode(a).require.toBase64
          _ <- Stream.eval(Sync[F].delay(ws.send(str)))
        } yield ()
      }

      Client[F, A](stream, in, out, promises, uri, haltWhenTrue)
    }
}
