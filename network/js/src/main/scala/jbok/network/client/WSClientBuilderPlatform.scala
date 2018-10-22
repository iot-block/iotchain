package jbok.network.client

import java.net.URI

import cats.effect.{ConcurrentEffect, IO, Sync}
import cats.implicits._
import fs2._
import fs2.concurrent.Queue
import jbok.common.execution._
import org.scalajs.dom
import org.scalajs.dom._
import scodec.Codec
import scodec.bits.BitVector

import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

class WSClientBuilderPlatform[F[_], A: Codec](implicit F: ConcurrentEffect[F]) extends ClientBuilder[F, A] {

  override def connect(
      to: URI,
      pipe: Pipe[F, A, A],
      reuseAddress: Boolean,
      sendBufferSize: Int,
      receiveBufferSize: Int,
      keepAlive: Boolean,
      noDelay: Boolean
  ): Stream[F, Unit] = {
    val url = s"ws://${to.getHost}:${to.getPort}"

    println(s"connecting to ${url}")

    val open: F[dom.WebSocket] = for {
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

    val use = (ws: dom.WebSocket) => {
      for {
        queue <- Stream.eval(Queue.unbounded[F, A])
        _ = ws.onmessage = { event: MessageEvent =>
          val arr = event.data.asInstanceOf[ArrayBuffer]
          val bits = BitVector(TypedArrayBuffer.wrap(arr))
          val a    = Codec[A].decode(bits).require.value
          F.runAsync(queue.enqueue1(a))(_ => IO.unit).unsafeRunSync()
        }
        a <- queue.dequeue.through(pipe)
        _ = println(s"sending request ${a}")
        str = Codec[A].encode(a).require.toBase64
        _ <- Stream.eval(Sync[F].delay(ws.send(str)))
      } yield ()
    }

    val release = (ws: dom.WebSocket) => {
      F.delay(ws.close(0, ""))
    }

    Stream
      .bracket[F, dom.WebSocket](open)(release).flatMap(use)
      .onFinalize(F.delay(println("websocket client stream finalized")))
  }
}

object WSClientBuilderPlatform {
  def apply[F[_]: ConcurrentEffect, A: Codec] = new WSClientBuilderPlatform[F, A]
}
