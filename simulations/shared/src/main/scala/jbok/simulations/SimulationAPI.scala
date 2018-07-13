package jbok.simulations

import fs2.async.mutable.Topic
import io.circe.generic.JsonCodec
import jbok.core.FullNode

@JsonCodec
case class NodeInfo(
    id: String,
    rpcAddress: NetAddress,
    p2pAddress: NetAddress
) {
  override def toString: String = s"NodeInfo(id=${id}, rpc=${rpcAddress}, p2p=${p2pAddress})"
}

object NodeInfo {
  def apply[F[_]](node: FullNode[F]): NodeInfo = NodeInfo(
    node.id,
    node.rpcBindAddress,
    node.p2pBindAddress
  )
}

trait SimulationAPI[F[_]] {
  def startNetwork: F[Unit]

  def stopNetwork: F[Unit]

  def getNodes: F[List[NodeInfo]]

  def createNodes(n: Int): F[List[NodeInfo]]

  def getNodeInfo(id: String): F[NodeInfo]

  def startNode(id: String): F[NodeInfo]

  def stopNode(id: String): F[NodeInfo]

  def connect(topology: String): F[Unit]

  val events: Topic[F, Option[SimulationEvent]]
}
