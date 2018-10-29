package jbok.network

import java.net.InetSocketAddress

import fs2._
import jbok.network.common.RequestId
import scodec.Codec

import scala.concurrent.duration.FiniteDuration

/**
  * wraps a raw connection that can read and write bytes
  * support request response pattern by [[RequestId]] and [[cats.effect.concurrent.Deferred]]
  */
trait Connection[F[_]] {
  def write[A: Codec](a: A, timeout: Option[FiniteDuration] = None): F[Unit]

  def writes[A: Codec](timeout: Option[FiniteDuration] = None): Sink[F, A]

  def read[A: Codec](timeout: Option[FiniteDuration] = None, maxBytes: Int = 256 * 1024): F[A]

  def reads[A: Codec](timeout: Option[FiniteDuration] = None, maxBytes: Int = 256 * 1024): Stream[F, A]

  def readsAndResolve[A: Codec: RequestId](timeout: Option[FiniteDuration] = None, maxBytes: Int = 256 * 1024): Stream[F, A]

  def request[A: Codec: RequestId](a: A, timeout: Option[FiniteDuration] = None): F[A]

  def close: F[Unit]

  def isIncoming: Boolean

  def localAddress: InetSocketAddress

  def remoteAddress: InetSocketAddress
}
