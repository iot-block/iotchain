package jbok.p2p.transport

import akka.actor.Actor
import akka.event.LoggingReceive
import cats.Id

trait TransportActor extends Actor with Transport[Id] {
  def receiveTransportOp: Receive = {
    case TransportOp.Bind(local)              => bind(local)
    case TransportOp.Unbind                   => unbind()
    case TransportOp.Dial(remote)             => dial(remote)
    case TransportOp.Close(remote, direction) => close(remote, direction)
  }

  def receiveTransportEvt: Receive = {
    case x: TransportEvent =>
      x match {
        case TransportEvent.Bound(local)                    => bound(local)
        case TransportEvent.BindingFailed(local)            => bindingFailed(local)
        case TransportEvent.Unbound(local)                  => unbound(local)
        case TransportEvent.Connected(conn, session)        => connected(conn, session)
        case TransportEvent.ConnectingFailed(remote, cause) => connectingFailed(remote, cause)
        case TransportEvent.Disconnected(conn, cause)       => disconnected(conn, cause)
      }

      context.parent ! x
  }

  override def receive: Receive = LoggingReceive {
    receiveTransportOp orElse receiveTransportEvt
  }
}
