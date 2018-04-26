package jbok.p2p.peer

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive
import cats.Monad
import cats.implicits._
import jbok.p2p.transport.{TransportEvent, TransportOp}

import scala.util.Random

class PeerManagerActor[F[_]: Monad](
    transport: ActorRef,
    store: PeerStore[F],
    config: PeerManagerConfig = PeerManagerConfig.defaultConfig)
    extends Actor
    with PeerManager[F] {
  override def knownPeers: F[List[PeerInfo]] =
    for {
      ids <- store.ids
      peers <- ids.traverse(id => store.get(id)).map(_.flatten)
    } yield peers

  override def connectedPeers: F[List[PeerInfo]] = ???

  override def discover(): F[Unit] = ???

  override def connect(): F[Unit] =
    for {
      connected <- connectedPeers
      known <- knownPeers
      demand = config.minConnectedPeers - connected.length
      available = known.diff(connected)
      toBeConnected = Random.shuffle(available).take(demand)
    } yield {
      toBeConnected.foreach(pi => transport ! TransportOp.Dial(???))
      ()
    }

  def receiveTransportEvent: Receive = {
    case TransportEvent.Connected(conn, session) => ???

    case TransportEvent.Disconnected(conn, cause) => ???
  }

  def receiveOp: Receive = {
    case PeerManagerOp.Discover => discover()
    case PeerManagerOp.Connect => connect()
  }

  override def receive: Receive = LoggingReceive {
    receiveOp.orElse(receiveTransportEvent)
  }
}
