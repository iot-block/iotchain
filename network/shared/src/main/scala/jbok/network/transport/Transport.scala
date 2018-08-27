package jbok.network.transport

import java.net.InetSocketAddress

import fs2._
import jbok.network.Connection

sealed trait TransportEvent[+A]
object TransportEvent {
  case class Add(remote: InetSocketAddress, incoming: Boolean)  extends TransportEvent[Nothing]
  case class Drop(remote: InetSocketAddress, incoming: Boolean) extends TransportEvent[Nothing]
  case class Received[A](remote: InetSocketAddress, a: A)       extends TransportEvent[A]
}

trait Transport[F[_], A] {
  def connect(remote: InetSocketAddress, onConnect: Connection[F, A] => F[Unit]): F[Unit]

  def disconnect(remote: InetSocketAddress): F[Unit]

  def getConnected: F[Map[InetSocketAddress, Connection[F, A]]]

  def listen(bind: InetSocketAddress,
             onConnect: Connection[F, A] => F[Unit],
             maxConcurrent: Int = Int.MaxValue,
             receiveBufferSize: Int = 256 * 1024): F[Unit]

  def stop: F[Unit]

  def broadcast(a: A): F[Unit]

  def write(remote: InetSocketAddress, a: A): F[Unit]

  def writes: Sink[F, (InetSocketAddress, A)]

  def request(remote: InetSocketAddress, a: A): F[Option[A]]

  def read(remote: InetSocketAddress): F[Option[A]]

  def reads(remote: InetSocketAddress): Stream[F, A]

  def subscribe(maxQueued: Int = 64): Stream[F, TransportEvent[A]]
}
