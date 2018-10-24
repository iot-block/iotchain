package jbok.network

import java.net.InetSocketAddress

import fs2._

import scala.concurrent.duration.FiniteDuration

trait Connection[F[_], A] {
  def write(a: A, timeout: Option[FiniteDuration] = None): F[Unit]

  def writes(timeout: Option[FiniteDuration] = None): Sink[F, A]

  def read(timeout: Option[FiniteDuration] = None, maxBytes: Int = 256 * 1024): F[A]

  def reads(timeout: Option[FiniteDuration] = None, maxBytes: Int = 256 * 1024): Stream[F, A]

  def request(a: A, timeout: Option[FiniteDuration] = None): F[A]

  def close: F[Unit]

  def isIncoming: Boolean

  def localAddress: InetSocketAddress

  def remoteAddress: InetSocketAddress
}
