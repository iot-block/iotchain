package jbok.network.common

import java.net.InetSocketAddress

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, Effect, Sync, Timer}
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

  def socketToConnection[F[_]: ConcurrentEffect, A: Codec: RequestId](
      socket: Socket[F],
      incoming: Boolean
  )(implicit T: Timer[F]): F[Connection[F, A]] =
    for {
      local    <- socket.localAddress.map(_.asInstanceOf[InetSocketAddress])
      remote   <- socket.remoteAddress.map(_.asInstanceOf[InetSocketAddress])
      promises <- Ref.of[F, Map[String, Deferred[F, A]]](Map.empty)
    } yield
      new Connection[F, A] {
        override def write(a: A, timeout: Option[FiniteDuration]): F[Unit] =
          encode[F, A](a).flatMap(chunk => socket.write(chunk, timeout))

        override def writes(timeout: Option[FiniteDuration]): Sink[F, A] =
          _.flatMap(encodeStream[F, A]).to(socket.writes(timeout))

        override def read(timeout: Option[FiniteDuration], maxBytes: Int): F[A] =
          for {
            chunk <- socket.read(maxBytes, timeout).map(_.get)
            a     <- decodeChunk[F, A](chunk)
          } yield a

        override def reads(timeout: Option[FiniteDuration], maxBytes: Int): Stream[F, A] =
          decodeStream[F, A](socket.reads(maxBytes, timeout))
            .evalMap { a =>
              for {
                idOpt <- Sync[F].delay(RequestId[A].id(a))
                _ <- idOpt match {
                  case None => Sync[F].unit
                  case Some(id) =>
                    promises.get.map(_.get(id)).flatMap {
                      case Some(p) => p.complete(a)
                      case None    => Sync[F].unit
                    }
                }
              } yield a
            }

        override def request(a: A, timeout: Option[FiniteDuration] = None): F[A] =
          for {
            id      <- Sync[F].delay(RequestId[A].id(a).getOrElse(""))
            promise <- Deferred[F, A]
            _       <- promises.update(_ + (id -> promise))
            _       <- write(a, timeout)
            resp    <- timeout.fold(promise.get)(t => promise.get.timeout(t))
            _       <- promises.update(_ - id)
          } yield resp

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
