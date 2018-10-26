package jbok.app.simulations

import _root_.io.circe.generic.JsonCodec
import cats.effect.IO
import fs2._
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.models.{Account, Address, Block, SignedTransaction}

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

  def createNodes(n: Int): IO[List[NodeInfo]]

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

  def events: Stream[IO, SimulationEvent]

  // simulation txs
  def submitStxsToNetwork(nStx: Int, t: String): IO[Unit]

  def submitStxsToNode(nStx: Int, t: String, id: String): IO[Unit]

  // get status of blocks
  def getBestBlock: IO[List[Block]]

  def getBlocksByNumber(number: BigInt): IO[List[Block]]

  def getShakedPeerID: IO[List[List[String]]]

  def getPendingTx: IO[List[List[SignedTransaction]]]

  def getCoin(address: Address, value: BigInt): IO[Unit]
}
