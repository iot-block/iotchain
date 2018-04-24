package jbok.p2p.transport

import java.net.InetSocketAddress

import akka.util.ByteString
import jbok.p2p.connection.{Connection, Direction}

trait Transport {
  def bind(local: InetSocketAddress)

  def bound(local: InetSocketAddress)

  def bindingFailed(local: InetSocketAddress)

  def unbind()

  def unbound()

  def dial(remote: InetSocketAddress)

  def connected(conn: Connection)

  def connectingFailed(remote: InetSocketAddress)

  def close(remote: InetSocketAddress, direction: Option[Direction] = None)

  def disconnected(conn: Connection, cause: String)

  def write(data: ByteString)
}
