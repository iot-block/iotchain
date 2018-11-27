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
    port: Int,
    rpcPort: Int
) {
  val addr                      = s"$interface:$port"
  val rpcAddr                   = s"ws://$interface:$rpcPort"
  override def toString: String = s"NodeInfo(id=${id}, addr=${addr}, rpcAddr=${rpcAddr})"
}

trait SimulationAPI {
  // simulation network
  def getAccounts(): IO[List[(Address, Account)]]

  def createNodesWithMiner(n: Int, m: Int): IO[List[NodeInfo]]

  def deleteNode(id: String): IO[Unit]

  def startNetwork: IO[Unit]

  def stopNetwork: IO[Unit]

  def setMiner(ids: List[String]): IO[Unit]

  def getMiners: IO[List[NodeInfo]]

  def stopMiners(ids: List[String]): IO[Unit]

  def getNodes: IO[List[NodeInfo]]

  def getNodeInfo(id: String): IO[NodeInfo]

  def startNode(id: String): IO[Unit]

  def stopNode(id: String): IO[Unit]

  def connect(topology: String): IO[Unit]

  // simulation txs
  def submitStxsToNetwork(nStx: Int, t: String): IO[Unit]

  def submitStxsToNode(nStx: Int, t: String, id: String): IO[Unit]

  // get status of blocks
  def getCoin(address: Address, value: BigInt): IO[Unit]
}
