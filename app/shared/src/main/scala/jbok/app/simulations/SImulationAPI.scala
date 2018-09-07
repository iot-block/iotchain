package jbok.app.simulations

import _root_.io.circe.generic.JsonCodec
import cats.effect.IO
import fs2._
import jbok.core.FullNode
import jbok.network.NetAddress
import jbok.network.rpc.RpcAPI

@JsonCodec
case class SimulationEvent(source: String, target: String, message: String, time: Long = System.currentTimeMillis())

@JsonCodec
case class NodeInfo(
    id: String,
    rpcAddress: NetAddress,
    p2pAddress: NetAddress
) {
  override def toString: String = s"NodeInfo(id=${id}, rpc=${rpcAddress}, p2p=${p2pAddress})"
}

object NodeInfo {
  def apply[F[_]](fullNode: FullNode[F]): NodeInfo =
    NodeInfo(fullNode.id, fullNode.config.network.rpcBindAddress, fullNode.config.network.peerBindAddress)
}

trait SimulationAPI extends RpcAPI {
  def startNetwork: Response[Unit]

  def stopNetwork: Response[Unit]

  def getNodes: Response[List[NodeInfo]]

  def createNodes(n: Int): Response[List[NodeInfo]]

  def getNodeInfo(id: String): Response[NodeInfo]

  def startNode(id: String): Response[Unit]

  def stopNode(id: String): Response[Unit]

  def connect(topology: String): Response[Unit]

  def events: Stream[IO, SimulationEvent]
}
