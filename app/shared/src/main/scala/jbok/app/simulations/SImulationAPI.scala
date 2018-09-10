package jbok.app.simulations

import _root_.io.circe.generic.JsonCodec
import cats.effect.IO
import fs2._
import jbok.core.FullNode
import jbok.network.NetAddress

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

trait SimulationAPI {
  def startNetwork: IO[Unit]

  def stopNetwork: IO[Unit]

  def getNodes: IO[List[NodeInfo]]

  def createNodes(n: Int): IO[List[NodeInfo]]

  def getNodeInfo(id: String): IO[NodeInfo]

  def startNode(id: String): IO[Unit]

  def stopNode(id: String): IO[Unit]

  def connect(topology: String): IO[Unit]

  def events: Stream[IO, SimulationEvent]
}
