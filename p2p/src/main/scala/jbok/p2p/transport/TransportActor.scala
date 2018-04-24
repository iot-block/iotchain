package jbok.p2p.transport

import akka.actor.Actor
import akka.event.LoggingReceive

trait TransportActor extends Actor with Transport {
  def receiveTransportOp: Receive = {
    case TransportOp.Bind(local)              => bind(local)
    case TransportOp.Unbind                   => unbind()
    case TransportOp.Dial(remote)             => dial(remote)
    case TransportOp.Close(remote, direction) => close(remote, direction)
    case TransportOp.Write(data)              => write(data)
  }

  def receiveTransportEvt: Receive = {
    case TransportEvent.Bound(local)              => bound(local)
    case TransportEvent.BindingFailed(local)      => bindingFailed(local)
    case TransportEvent.Connected(conn)           => connected(conn)
    case TransportEvent.ConnectingFailed(remote)  => connectingFailed(remote)
    case TransportEvent.Disconnected(conn, cause) => disconnected(conn, cause)
  }

  override def receive: Receive = LoggingReceive {
    receiveTransportOp orElse receiveTransportEvt
  }
}
