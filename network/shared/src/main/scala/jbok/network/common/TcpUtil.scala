package jbok.network.common

import java.net.SocketAddress

import cats.effect.{ConcurrentEffect, Effect, Sync}
import cats.implicits._
import fs2._
import fs2.io.tcp.Socket
import jbok.network.Connection
import scodec.Codec
import scodec.bits.BitVector
import scodec.stream.decode

import scala.concurrent.duration.FiniteDuration

private[jbok] object TcpUtil {
  def decodeChunk[F[_]: Effect, A: Codec](chunk: Chunk[Byte]): F[A] =
    Sync[F].delay(Codec[A].decode(BitVector(chunk.toArray)).require.value)

  def decodeStream[F[_]: Effect, A: Codec](s: Stream[F, Byte]): Stream[F, A] =
    s.chunks.flatMap(chunk => {
      val bits = BitVector(chunk.toArray)
      decode.many[A].decode[F](bits)
    })

  def encode[F[_]: Effect, A: Codec](a: A): F[Chunk[Byte]] =
    Sync[F].delay(Chunk.array(Codec[A].encode(a).require.toByteArray))

  def encodeStream[F[_]: Effect, A: Codec](a: A): Stream[F, Byte] =
    for {
      chunk <- Stream.eval(encode[F, A](a))
      s     <- Stream.chunk(chunk).covary[F]
    } yield s

  def socketToConnection[F[_]: ConcurrentEffect, A: Codec](socket: Socket[F]): Connection[F, A] =
    new Connection[F, A] {
      override def write(a: A, timeout: Option[FiniteDuration]): F[Unit] =
        encode[F, A](a).flatMap(chunk => socket.write(chunk, timeout))

      override def writes(timeout: Option[FiniteDuration]): Sink[F, A] =
        _.flatMap(encodeStream[F, A]).to(socket.writes(timeout))

      override def read(timeout: Option[FiniteDuration], maxBytes: Int): F[Option[A]] =
        for {
          chunkOpt <- socket.read(maxBytes, timeout)
          aOpt <- chunkOpt match {
            case Some(chunk) => decodeChunk[F, A](chunk).map(_.some)
            case None        => none[A].pure[F]
          }
        } yield aOpt

      override def reads(timeout: Option[FiniteDuration], maxBytes: Int): Stream[F, A] =
        decodeStream[F, A](socket.reads(maxBytes, timeout))

      override def endOfInput: F[Unit] =
        socket.endOfInput

      override def endOfOutput: F[Unit] =
        socket.endOfOutput

      override def remoteAddress: F[SocketAddress] =
        socket.remoteAddress

      override def localAddress: F[SocketAddress] =
        socket.localAddress

      override def close: F[Unit] =
        socket.close
    }
}
