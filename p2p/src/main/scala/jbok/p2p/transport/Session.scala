package jbok.p2p.transport

import akka.actor.{Actor, ActorSystem}
import akka.event.LoggingReceive
import akka.stream.scaladsl.SourceQueueWithComplete
import jbok.p2p.connection.Connection

import scala.reflect.ClassTag

class Session[M: ClassTag](conn: Connection, queue: SourceQueueWithComplete[M])(implicit system: ActorSystem)
    extends Actor {
  import system.dispatcher

  queue.watchCompletion.onComplete(_ => context stop self)

  override def receive = LoggingReceive {
    // server push
    case data: M =>
      queue.offer(data)
  }

  override def postStop() = {
    queue.complete()
    context.parent ! TransportEvent.Disconnected(conn, "close")
  }
}
