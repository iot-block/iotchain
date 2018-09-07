package jbok.network

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.Functor
import cats.effect.Effect
import cats.implicits._
import fs2._
import fs2.io.tcp.Socket
import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

package object tcp {
  private[this] val log = org.log4s.getLogger

  def decodePipe[F[_]]: Pipe[F, Byte, ByteVector] =
    _.chunks.map(chunks => ByteVector(chunks.toArray))

  def encodePipe[F[_]]: Pipe[F, ByteVector, Byte] =
    _.flatMap[Byte](m => Stream.chunk(Chunk.array(m.toArray)).covary[F])

  def bytesToChunk(bytes: ByteVector): Chunk[Byte] = Chunk.array(bytes.toArray)

  def chunkToBytes(chunk: Chunk[Byte]): ByteVector = ByteVector(chunk.toArray)

  implicit class SocketOp[F[_]](val socket: Socket[F]) extends AnyVal {
    def writeBytes(bytes: ByteVector, timeout: Option[FiniteDuration] = None): F[Unit] =
      socket.write(bytesToChunk(bytes), timeout)

    def readBytesOpt(timeout: Option[FiniteDuration] = None)(implicit F: Functor[F]): F[Option[ByteVector]] =
      socket.read(256 * 1024, None).map(_.map(chunkToBytes))

    def readBytes(timeout: Option[FiniteDuration] = None)(implicit F: Functor[F]): F[ByteVector] =
      readBytesOpt(timeout).map(_.get)

    def readBytesStream: Stream[F, ByteVector] =
      socket.reads(256 * 1024).chunks.map(chunkToBytes)
  }

  def server[F[_], A](
      port: Int,
      host: String = "localhost",
      maxOpen: Int = Int.MaxValue
  )(f: Socket[F] => Stream[F, A])(
      implicit F: Effect[F],
      AG: AsynchronousChannelGroup,
      EC: ExecutionContext
  ): Stream[F, Stream[F, A]] =
    fs2.io.tcp
      .serverWithLocalAddress[F](new InetSocketAddress(host, port))
      .map {
        case Left(bindAddr) =>
          log.info(s"listening on ${bindAddr}")
          Stream.empty.covary[F]

        case Right(s) =>
          s.flatMap(f)
            .onFinalize(F.delay(log.info(s"connection ${s} closed")))
      }

  def client[F[_], A](
      port: Int,
      host: String = "localhost"
  )(f: Socket[F] => Stream[F, A])(
      implicit F: Effect[F],
      AG: AsynchronousChannelGroup,
      EC: ExecutionContext
  ): Stream[F, A] =
    fs2.io.tcp
      .client[F](new InetSocketAddress(host, port), keepAlive = true, noDelay = true)
      .flatMap(f)
      .onFinalize(F.delay(log.info(s"connection to ${host}:${port} closed")))
}
