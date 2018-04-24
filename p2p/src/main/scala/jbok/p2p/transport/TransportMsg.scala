package jbok.p2p.transport

import java.net.InetSocketAddress

import akka.actor.ActorRef
import jbok.p2p.connection.{Connection, Direction}

sealed trait TransportOp
object TransportOp {
  case class Bind(local: InetSocketAddress) extends TransportOp
  case object Unbind                        extends TransportOp

  case class Dial(remote: InetSocketAddress)                                        extends TransportOp
  case class Close(remote: Option[InetSocketAddress], direction: Option[Direction]) extends TransportOp
}

sealed trait TransportEvent
object TransportEvent {
  case class Bound(local: InetSocketAddress)         extends TransportEvent
  case class BindingFailed(local: InetSocketAddress) extends TransportEvent
  case class Unbound(local: InetSocketAddress)       extends TransportEvent

  case class Connected(conn: Connection, session: ActorRef)                extends TransportEvent
  case class ConnectingFailed(remote: InetSocketAddress, cause: Throwable) extends TransportEvent

  case class Disconnected(conn: Connection, cause: String) extends TransportEvent
}
