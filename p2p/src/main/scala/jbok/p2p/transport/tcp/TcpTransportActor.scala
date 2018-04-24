package jbok.p2p.transport.tcp

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props, SupervisorStrategy}
import akka.io.Tcp
import akka.stream.scaladsl.{Keep, Sink, Tcp => STcp}
import akka.stream.{Materializer, OverflowStrategy}
import jbok.p2p.connection.{Connection, Direction}
import jbok.p2p.protocol.Protocol
import jbok.p2p.transport.{Session, TransportActor, TransportEvent}

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

class TcpTransportActor[M: ClassTag](protocol: Protocol[M])(implicit system: ActorSystem, fm: Materializer)
    extends TransportActor {
  val connections = mutable.Map[Connection, ActorRef]()

  var listenAddr: Option[InetSocketAddress] = None

  var bindingActor: Option[ActorRef] = None

  import context.dispatcher

  // no recovery for broken connections
  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def filterConnections(remote: Option[InetSocketAddress], direction: Option[Direction]) = {
    val filterByRemote = remote match {
      case Some(addr) => connections.filterKeys(_.remote == addr)
      case None       => connections
    }

    val filtered = direction match {
      case Some(d) => filterByRemote.filterKeys(_.direction == d)
      case None    => filterByRemote
    }

    filtered
  }

  override def bind(local: InetSocketAddress): Unit = {
    val binding = STcp()
      .bind(local.getHostName, local.getPort)
      .to(Sink.foreach(ic => {
        val flow = protocol
          .flow(16, OverflowStrategy.backpressure)
          .mapMaterializedValue(queue => {
            val conn    = Connection(ic.remoteAddress, ic.localAddress, Direction.Inbound)
            val session = context.actorOf(Props(new Session(conn, queue)), "inbound-session")
            self ! TransportEvent.Connected(conn, session)
          })
        ic.handleWith(flow)
      }))
      .run()

    binding.onComplete {
      case Success(bind) => self ! TransportEvent.Bound(bind.localAddress)
      case Failure(_)    => self ! TransportEvent.BindingFailed(local)
    }
  }

  override def bound(local: InetSocketAddress): Unit = {
    listenAddr = Some(local)
    bindingActor = Some(sender)
  }

  override def bindingFailed(local: InetSocketAddress): Unit = context stop self

  override def unbind(): Unit = {
    if (bindingActor.isDefined) {
      bindingActor.get ! Tcp.Unbind
    }
  }

  override def unbound(local: InetSocketAddress): Unit = {
    listenAddr = None
    bindingActor = None
  }

  override def dial(remote: InetSocketAddress): Unit = {
    val (ocFut, queue) =
      STcp().outgoingConnection(remote).joinMat(protocol.flow(16, OverflowStrategy.backpressure))(Keep.both).run()

    ocFut.onComplete {
      case Success(oc) =>
        val conn    = Connection(oc.remoteAddress, oc.localAddress, Direction.Outbound)
        val session = context.actorOf(Props(new Session(conn, queue)), "outbound-session")
        self ! TransportEvent.Connected(conn, session)

      case Failure(ex) =>
        self ! TransportEvent.ConnectingFailed(remote, ex)
    }
  }

  override def connected(conn: Connection, session: ActorRef): Unit = connections += conn -> session

  override def connectingFailed(remote: InetSocketAddress, cause: Throwable): Unit = ()

  override def close(remote: Option[InetSocketAddress], direction: Option[Direction]): Unit = {
    val filtered = filterConnections(remote, direction)
    filtered.foreach(_._2 ! PoisonPill)
  }

  override def disconnected(conn: Connection, cause: String): Unit = {
    connections -= conn
  }
}
