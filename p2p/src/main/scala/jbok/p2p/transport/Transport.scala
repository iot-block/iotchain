package jbok.p2p.transport

import java.net.InetSocketAddress

import akka.actor.ActorRef
import jbok.p2p.connection.{Connection, Direction}

trait Transport {
  def bind(local: InetSocketAddress)

  def bound(local: InetSocketAddress)

  def bindingFailed(local: InetSocketAddress)

  def unbind()

  def unbound(local: InetSocketAddress)

  def dial(remote: InetSocketAddress)

  def connected(conn: Connection, session: ActorRef)

  def connectingFailed(remote: InetSocketAddress, cause: Throwable)

  def close(remote: Option[InetSocketAddress], direction: Option[Direction])

  def disconnected(conn: Connection, cause: String)
}
