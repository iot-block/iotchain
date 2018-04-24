package jbok.p2p.transport

import java.net.InetSocketAddress

import akka.util.ByteString
import jbok.p2p.connection.{Connection, Direction}

sealed trait TransportOp
object TransportOp {
  case class Bind(local: InetSocketAddress) extends TransportOp
  case object Unbind                        extends TransportOp

  case class Dial(remote: InetSocketAddress)                                       extends TransportOp
  case class Close(remote: InetSocketAddress, direction: Option[Direction] = None) extends TransportOp

  case class Write(data: ByteString) extends TransportOp
}

sealed trait TransportEvent
object TransportEvent {
  case class Bound(local: InetSocketAddress)         extends TransportEvent
  case class BindingFailed(local: InetSocketAddress) extends TransportEvent

  case class Connected(conn: Connection)                 extends TransportEvent
  case class ConnectingFailed(remote: InetSocketAddress) extends TransportEvent

  case class Disconnected(conn: Connection, cause: String) extends TransportEvent
}
