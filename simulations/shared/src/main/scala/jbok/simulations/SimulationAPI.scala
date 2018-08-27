package jbok.simulations

import _root_.io.circe.generic.JsonCodec
import cats.effect.IO
import fs2._
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

trait SimulationAPI extends RpcAPI {
  def startNetwork: Response[Unit]

  def stopNetwork: Response[Unit]

  def getNodes: Response[List[NodeInfo]]

  def createNodes(n: Int): Response[List[NodeInfo]]

  def getNodeInfo(id: String): Response[NodeInfo]

  def startNode(id: String): Response[NodeInfo]

  def stopNode(id: String): Response[NodeInfo]

  def connect(topology: String): Response[Unit]

  val events: Stream[IO, SimulationEvent]
}
