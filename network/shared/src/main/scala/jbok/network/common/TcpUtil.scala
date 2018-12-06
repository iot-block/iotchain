package jbok.network.common

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import fs2.io.tcp.Socket
import jbok.network.Connection
import scodec.Codec
import scodec.bits.BitVector
import scodec.stream.decode

private[jbok] object TcpUtil {
  def decodeChunk[F[_]: Sync, A: Codec](chunk: Chunk[Byte]): F[A] =
    Sync[F].delay(Codec[A].decode(BitVector(chunk.toArray)).require.value)

  def decodeStream[F[_]: Effect, A: Codec](s: Stream[F, Byte]): Stream[F, A] =
    s.chunks.flatMap(chunk => {
      val bits = BitVector(chunk.toArray)
      decode.many[A].decode[F](bits)
    })

  def encode[F[_]: Sync, A: Codec](a: A): F[Chunk[Byte]] =
    Sync[F].delay(Chunk.array(Codec[A].encode(a).require.toByteArray))

  def encodeStream[F[_]: Effect, A: Codec](a: A): Stream[F, Byte] =
    for {
      chunk <- Stream.eval(encode[F, A](a))
      s     <- Stream.chunk(chunk).covary[F]
    } yield s

  def socketToConnection[F[_], A: Codec: RequestId](
      resource: Resource[F, Socket[F]],
      incoming: Boolean,
      maxBytes: Int = 256 * 1024,
      maxQueued: Int = 64
  )(implicit F: ConcurrentEffect[F], CS: ContextShift[F], T: Timer[F]): F[Connection[F, A]] =
    for {
      in           <- Queue.bounded[F, A](maxQueued)
      out          <- Queue.bounded[F, A](maxQueued)
      promises     <- Ref.of[F, Map[String, Deferred[F, A]]](Map.empty)
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield {
      val sink: Sink[F, A] = { input =>
        input
          .evalMap[F, Unit] { a =>
            RequestId[A].id(a) match {
              case "" => in.enqueue1(a)
              case id =>
                promises.get.flatMap(_.get(id) match {
                  case Some(p) => p.complete(a)
                  case None    => in.enqueue1(a)
                })
            }
          }
      }

      val stream = Stream
        .resource(resource)
        .flatMap { socket =>
          out.dequeue
            .flatMap(encodeStream[F, A])
            .to(socket.writes(None))
            .mergeHaltBoth(decodeStream[F, A](socket.reads(maxBytes, None)).to(sink))
            .onFinalize(haltWhenTrue.set(true))
        }
        .interruptWhen(haltWhenTrue)

      Connection(stream, in, out, promises, incoming, haltWhenTrue)
    }
}
