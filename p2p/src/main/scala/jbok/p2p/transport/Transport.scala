package jbok.p2p.transport

import java.net.InetSocketAddress

import akka.actor.ActorRef
import jbok.p2p.connection.{Connection, Direction}

trait Transport[F[_]] {
  def bind(local: InetSocketAddress): F[Unit]

  def bound(local: InetSocketAddress): F[Unit]

  def bindingFailed(local: InetSocketAddress): F[Unit]

  def unbind(): F[Unit]

  def unbound(local: InetSocketAddress): F[Unit]

  def dial(remote: InetSocketAddress): F[Unit]

  def connected(conn: Connection, session: ActorRef): F[Unit]

  def connectingFailed(remote: InetSocketAddress, cause: Throwable): F[Unit]

  def close(remote: Option[InetSocketAddress], direction: Option[Direction]): F[Unit]

  def disconnected(conn: Connection, cause: String): F[Unit]
}
