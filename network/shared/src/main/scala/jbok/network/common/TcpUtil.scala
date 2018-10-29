package jbok.network.common

import java.net.InetSocketAddress
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeoutException

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.io.tcp.Socket
import jbok.network.{Connection, NetworkErr}
import scodec.Codec
import scodec.bits.BitVector
import scodec.stream.decode

import scala.concurrent.duration.FiniteDuration

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

  def socketToConnection[F[_]](
      socket: Socket[F],
      incoming: Boolean
  )(implicit F: ConcurrentEffect[F], T: Timer[F]): F[Connection[F]] =
    for {
      local    <- socket.localAddress.map(_.asInstanceOf[InetSocketAddress])
      remote   <- socket.remoteAddress.map(_.asInstanceOf[InetSocketAddress])
      promises <- Ref.of[F, Map[String, Deferred[F, Any]]](Map.empty)
    } yield
      new Connection[F] {
        private def onError[A](e: Throwable): F[A] = e match {
          case _: InterruptedByTimeoutException | _: TimeoutException => F.raiseError[A](NetworkErr.Timeout)
          case _                                                      => F.raiseError[A](e)
        }

        override def write[A: Codec](a: A, timeout: Option[FiniteDuration]): F[Unit] =
          encode[F, A](a)
            .flatMap(chunk => socket.write(chunk, timeout))
            .attempt
            .flatMap[Unit] {
              case Right(_)                               => F.unit
              case Left(_: InterruptedByTimeoutException) => F.raiseError(NetworkErr.Timeout)
              case Left(e)                                => F.raiseError(e)
            }

        override def writes[A: Codec](timeout: Option[FiniteDuration]): Sink[F, A] =
          _.flatMap(encodeStream[F, A]).to(socket.writes(timeout))

        override def read[A: Codec](timeout: Option[FiniteDuration], maxBytes: Int): F[A] =
          for {
            chunk <- socket.read(maxBytes, timeout).flatMap[Chunk[Byte]] {
              case Some(chunk) => F.pure(chunk)
              case None        => F.raiseError(NetworkErr.AlreadyClosed)
            }
            a <- decodeChunk[F, A](chunk)
          } yield a

        override def reads[A: Codec](timeout: Option[FiniteDuration], maxBytes: Int): Stream[F, A] =
          decodeStream[F, A](socket.reads(maxBytes, timeout))

        override def readsAndResolve[A: Codec: RequestId](timeout: Option[FiniteDuration], maxBytes: Int): Stream[F, A] =
          decodeStream[F, A](socket.reads(maxBytes, timeout))
            .evalMap { a =>
              for {
                idOpt <- Sync[F].delay(RequestId[A].id(a))
                _ <- idOpt match {
                  case None => Sync[F].unit
                  case Some(id) =>
                    promises.get.map(_.get(id)).flatMap {
                      case Some(p) => p.complete(a).attempt.void
                      case None    => Sync[F].unit
                    }
                }
              } yield a
            }

        override def request[A: Codec: RequestId](a: A, timeoutOpt: Option[FiniteDuration] = None): F[A] = {
          val acquire =
            for {
              id      <- F.delay(RequestId[A].id(a).getOrElse(""))
              promise <- Deferred[F, Any]
              _       <- promises.update(_ + (id -> promise))
            } yield (id, promise)

          def use(t: (String, Deferred[F, Any])): F[A] =
            write(a, timeoutOpt) *>
              timeoutOpt
                .fold(t._2.get)(timeout => t._2.get.timeout(timeout))
                .flatMap(x => F.delay(x.asInstanceOf[A]))
                .handleErrorWith(onError)

          def release(t: (String, Deferred[F, Any])): F[Unit] =
            promises.update(_ - t._1)

          F.bracket[(String, Deferred[F, Any]), A](acquire)(use)(release)
        }

        override def close: F[Unit] =
          socket.close

        override def isIncoming: Boolean =
          incoming

        override val localAddress: InetSocketAddress =
          local

        override val remoteAddress: InetSocketAddress =
          remote

        override def toString: String =
          if (incoming) {
            s"tcp incoming ${localAddress} <- ${remoteAddress}"
          } else {
            s"tcp outgoing ${localAddress} -> ${remoteAddress}"
          }
      }
}
