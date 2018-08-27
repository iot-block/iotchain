package jbok.network

import java.net.SocketAddress

import fs2._

import scala.concurrent.duration.FiniteDuration

trait Connection[F[_], A] {
  def write(a: A, timeout: Option[FiniteDuration] = None): F[Unit]

  def writes(timeout: Option[FiniteDuration] = None): Sink[F, A]

  def read(timeout: Option[FiniteDuration] = None, maxBytes: Int = 256 * 1024): F[Option[A]]

  def reads(timeout: Option[FiniteDuration] = None, maxBytes: Int = 256 * 1024): Stream[F, A]

  def endOfInput: F[Unit]

  def endOfOutput: F[Unit]

  def remoteAddress: F[SocketAddress]

  def localAddress: F[SocketAddress]

  def close: F[Unit]
}