package jbok.app.simulations
import java.net.URI

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import cats.implicits._
import fs2.concurrent.Topic
import jbok.app.api.{NodeInfo, SimulationAPI, SimulationEvent}
import jbok.app.client.JbokClient
import jbok.core.models.{Account, Address}

import scala.concurrent.duration._

final case class Node(
    nodeInfo: NodeInfo,
    peerNodeUri: String,
    jbokClient: JbokClient
)

class SimulationImpl(
    topic: Topic[IO, Option[SimulationEvent]],
    id2Node: Ref[IO, Map[String, Node]]
)(implicit F: ConcurrentEffect[IO], T: Timer[IO], CS: ContextShift[IO])
    extends SimulationAPI {
  import SimulationImpl._
  private[this] val log = jbok.common.log.getLogger("SimulationImpl")

  override def getNodes: IO[List[NodeInfo]] = id2Node.get.map(_.values.toList.map(_.nodeInfo))

  override def getNodeInfo(id: String): IO[Option[NodeInfo]] =
    id2Node.get.map(_.get(id).map(_.nodeInfo))

  override def stopNode(id: String): IO[Unit] =
    for {
      nodes <- id2Node.get
      _     <- nodes.get(id).traverse(_.jbokClient.admin.stop)
    } yield ()

  override def getAccounts: IO[List[(Address, Account)]] = IO { List.empty }

  override def addNode(interface: String, port: Int): IO[Option[String]] =
    for {
      jbokClient <- jbok.app.client.JbokClient(new URI(s"ws://$interface:$port"))
      peerNodeUriOpt <- jbokClient.admin.peerNodeUri.timeout(requestTimeout).attempt.map {
        case Left(_)            => None
        case Right(peerNodeUri) => peerNodeUri.some
      }
      _ <- peerNodeUriOpt.traverse[IO, Unit] { peerNodeUri =>
        id2Node.update { id2NodeMap =>
          id2NodeMap + (peerNodeUri -> Node(NodeInfo(peerNodeUri, interface, port), peerNodeUri, jbokClient))
        }
      }
    } yield peerNodeUriOpt

  override def deleteNode(peerNodeUri: String): IO[Unit] =
    for {
      _ <- id2Node.update(_ - peerNodeUri)
    } yield ()
}

object SimulationImpl {
  val requestTimeout: FiniteDuration = 5.seconds

  def apply()(implicit F: ConcurrentEffect[IO], T: Timer[IO], CS: ContextShift[IO]): IO[SimulationImpl] =
    for {
      topic   <- Topic[IO, Option[SimulationEvent]](None)
      id2Node <- Ref.of[IO, Map[String, Node]](Map.empty)
    } yield new SimulationImpl(topic, id2Node)
}
