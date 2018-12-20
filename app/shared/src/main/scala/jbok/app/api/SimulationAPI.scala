package jbok.app.simulations

import _root_.io.circe.generic.JsonCodec
import cats.effect.IO
import jbok.core.models.{Account, Address}

@JsonCodec
case class SimulationEvent(source: String, target: String, message: String, time: Long = System.currentTimeMillis())

@JsonCodec
case class NodeInfo(
    id: String,
    interface: String,
    rpcPort: Int
) {
  val rpcAddr                   = s"ws://$interface:$rpcPort"
  override def toString: String = s"NodeInfo(identity=${id}, rpcAddr=${rpcAddr})"
}

trait SimulationAPI {
  // simulation network for test
  def createNodesWithMiner(n: Int, m: Int): IO[List[NodeInfo]]

  def getAccounts: IO[List[(Address, Account)]]

  def getCoin(address: Address, value: BigInt): IO[Unit]

  def getNodes: IO[List[NodeInfo]]

  def getNodeInfo(id: String): IO[Option[NodeInfo]]

  def stopNode(id: String): IO[Unit]

  // manage outside node
  def addNode(interface: String, port: Int): IO[Option[String]]

  def deleteNode(id: String): IO[Unit]

  // simulation txs
  def submitStxsToNetwork(nStx: Int, t: String): IO[Unit]

  def submitStxsToNode(nStx: Int, t: String, id: String): IO[Unit]
}
