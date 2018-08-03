package jbok.network.rlpx.handshake

import java.net.SocketAddress

import cats.effect.Effect
import cats.implicits._
import fs2._
import fs2.io.tcp.Socket
import jbok.network.tcp._
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration

trait AuthConnection[F[_]] {
  def write(bytes: ByteVector, timeout: Option[FiniteDuration] = None): F[Unit]

  def writes(timeout: Option[FiniteDuration] = None): Sink[F, ByteVector]

  def read(timeout: Option[FiniteDuration] = None): F[Option[ByteVector]]

  def reads: Stream[F, ByteVector]

  def endOfInput: F[Unit]

  def endOfOutput: F[Unit]

  def remoteAddress: F[SocketAddress]

  def localAddress: F[SocketAddress]

  def close: F[Unit]
}

object AuthConnection {
  private[this] val log = org.log4s.getLogger

  def connect[F[_]: Effect](
      socket: Socket[F],
      handshaker: AuthHandshaker[F],
      selfNodeId: String,
      timeout: Option[FiniteDuration] = None
  ): Stream[F, AuthConnection[F]] = {
    for {
      (initPacket, initHandshaker) <- Stream.eval(handshaker.initiate(selfNodeId))
      _ <- Stream.eval(socket.writeBytes(initPacket, timeout))
      _ = log.debug(s"write init packet, wait auth response")
      (result, remaining) <- Stream.eval(waitRemoteResponse(initHandshaker, socket, timeout))
      _ = log.debug(s"get auth result ${result}, remaining ${remaining}")
      handler = mkConnection(socket)
    } yield handler
  }

  def accept[F[_]: Effect](
      socket: Socket[F],
      handshaker: AuthHandshaker[F],
      timeout: Option[FiniteDuration] = None
  ): Stream[F, AuthConnection[F]] =
    for {
      (result, remaining) <- Stream.eval(waitRemoteInitiate(handshaker, socket, timeout))
      _ = log.debug(s"get auth result ${result}, remaining ${remaining}")
      handler = mkConnection(socket)
    } yield handler

  private[jbok] def mkConnection[F[_]: Effect](socket: Socket[F]): AuthConnection[F] = {
    val connection = new AuthConnection[F] {
      override def write(bytes: ByteVector, timeout: Option[FiniteDuration]): F[Unit] =
        socket.writeBytes(bytes, timeout)

      override def writes(timeout: Option[FiniteDuration]): Sink[F, ByteVector] =
        encodePipe.andThen(_.to(socket.writes(timeout)))

      override def read(timeout: Option[FiniteDuration]): F[Option[ByteVector]] =
        socket.readBytesOpt(timeout)

      override def reads: Stream[F, ByteVector] =
        socket.readBytesStream

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

    connection
  }

  private[jbok] def waitRemoteInitiate[F[_]: Effect](
      handshaker: AuthHandshaker[F],
      socket: Socket[F],
      timeout: Option[FiniteDuration]
  ): F[(AuthHandshakeResult, ByteVector)] =
    for {
      data <- socket.readBytes(timeout)
      (response, result, remaining) <- handshaker.handleInitialMessageAll(data)
      _ <- socket.writeBytes(response, timeout)
    } yield (result, remaining)

  private[jbok] def waitRemoteResponse[F[_]: Effect](
      handshaker: AuthHandshaker[F],
      socket: Socket[F],
      timeout: Option[FiniteDuration]
  ): F[(AuthHandshakeResult, ByteVector)] =
    for {
      data <- socket.readBytes(timeout)
      (result, remaining) <- handshaker.handleResponseMessageAll(data)
    } yield (result, remaining)
}
