package jbok.simulations

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2.async.Ref
import fs2.async.mutable.Topic
import io.circe.generic.JsonCodec
import jbok.core.FullNode
import jbok.core.configs.FullNodeConfig

import scala.concurrent.ExecutionContext
@JsonCodec
case class SimulationEvent(source: NodeInfo, target: NodeInfo, message: String, time: Long = System.currentTimeMillis())

//case class Conn[F[_]](
//    source: Node[F],
//    target: Node[F],
//    up: Boolean = false,
//    initiated: Long = System.currentTimeMillis()
//)

case class Ex(msg: String) extends RuntimeException(msg)
import jbok.simulations.Simulation._

class Simulation[F[_]](
    val topic: Topic[F, Option[SimulationEvent]],
    val nodes: Ref[F, Map[NodeId, FullNode[F]]]
)(implicit F: ConcurrentEffect[F], EC: ExecutionContext, T: Timer[F])
    extends SimulationAPI[F] {
  private[this] val log = org.log4s.getLogger

  val maxQueued: Int = 32

  override def startNetwork: F[Unit] = {
    log.info(s"network start all nodes")
    for {
      xs <- nodes.get
      _ <- xs.values.toList.map(x => startNode(x.id)).sequence
    } yield ()
  }

  override def stopNetwork: F[Unit] = {
    log.info(s"network stop all nodes")
    for {
      xs <- nodes.get
      _ <- xs.values.toList.map(x => stopNode(x.id)).sequence
    } yield ()
  }

  override def getNodes: F[List[NodeInfo]] =
    nodes.get.map(_.values.map(x => NodeInfo(x)).toList)

  override def createNodes(n: Int): F[List[NodeInfo]] = {
    val configs = (1 to n).toList.map(_ => FullNodeConfig.random())
    log.info(s"create ${n} node(s)")
    for {
      newNodes <- configs.traverse(FullNode.inMemory[F])
      _ <- nodes.modify(_ ++ newNodes.map(x => x.id -> x).toMap)
    } yield newNodes.map(x => NodeInfo(x))
  }

  override def getNodeInfo(id: NodeId): F[NodeInfo] =
    getNode(id).map(x => NodeInfo(x))

  override def startNode(id: NodeId): F[NodeInfo] =
    for {
      node <- getNode(id)
      _ <- node.start
    } yield NodeInfo(node)

  override def stopNode(id: NodeId): F[NodeInfo] =
    for {
      node <- getNode(id)
      _ <- node.stop
    } yield NodeInfo(node)

//  override def connectNode(id: NodeId, peer: NodeId): F[Unit] = {
//    log.info(s"connect node $id to $peer")
//    for {
//      conn <- initConn(id, peer)
//      _ <- topic.publish1(SimulationEvent(conn.source.nodeInfo, conn.target.nodeInfo, "connect").some)
//      api = conn.source.api
//      _ <- api.addPeer(conn.target.addr)
//    } yield ()
//  }
//
//  override def disconnectNode(id: NodeId, peer: NodeId): F[Unit] = {
//    log.info(s"disconnect node $id to $peer")
//    val conn = connections((id, peer))
//    for {
//      _ <- topic.publish1(SimulationEvent(conn.source.nodeInfo, conn.target.nodeInfo, "disConnect").some)
//      _ <- conn.source.api.removePeer(conn.target.addr)
//    } yield ()
//  }

  override def connect(topology: String): F[Unit] = topology match {
    case "ring" =>
      for {
        xs <- nodes.get.map(_.values.toList)
        _ <- xs.zipWithIndex.map { case (n, i) =>
            val next = xs((i + 1) % xs.length)
            n.peerManager.knownAddrManager.addKnownAddr(next.p2pBindAddress)
        }.sequence
      } yield ()
    case _ => F.raiseError(new RuntimeException(s"${topology} not supportted"))
  }

  override val events: Topic[F, Option[SimulationEvent]] = topic

  // helpers

  private def getNode(id: NodeId): F[FullNode[F]] =
    nodes.get.map(xs => xs(id))

//  private def getOrCreateConn(source: NodeId, target: NodeId): F[Conn[F]] = {
//    connections.get(source -> target) match {
//      case Some(conn) => conn.pure[F]
//      case None =>
//        for {
//          a <- getNode(source)
//          b <- getNode(target)
//          conn = Conn(a, b)
//        } yield conn
//
//    }
//  }
//
//  private def initConn(source: NodeId, target: NodeId): F[Conn[F]] = synchronized {
//    if (source == target) {
//      F.raiseError(Ex(s"refusing to connect to self ${source}"))
//    } else {
//      for {
//        conn <- getOrCreateConn(source, target)
//        x <- if (conn.up) {
//          F.raiseError(Ex(s"node $source and $target already connected"))
//        } else {
//          conn.copy(initiated = System.currentTimeMillis()).pure[F]
//        }
//      } yield x
//    }
//  }
}

object Simulation {
  type NodeId = String
  def apply[F[_]](implicit F: ConcurrentEffect[F], EC: ExecutionContext, T: Timer[F]): F[Simulation[F]] =
    for {
      topic <- fs2.async.topic[F, Option[SimulationEvent]](None)
      nodes <- fs2.async.refOf[F, Map[NodeId, FullNode[F]]](Map.empty)
    } yield new Simulation[F](topic, nodes)
}
