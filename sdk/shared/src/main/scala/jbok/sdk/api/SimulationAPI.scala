package jbok.sdk.api

import _root_.io.circe.generic.JsonCodec
import jbok.core.models.{Account, Address}

@JsonCodec
final case class SimulationEvent(
    source: String,
    target: String,
    message: String,
    time: Long = System.currentTimeMillis()
)

@JsonCodec
final case class NodeInfo(
    id: String,
    interface: String,
    rpcPort: Int
) {
  val rpcAddr                   = s"ws://$interface:$rpcPort"
  override def toString: String = s"NodeInfo(identity=${id}, rpcAddr=${rpcAddr})"
}

trait SimulationAPI[F[_]] {
  // simulation network for test
  def getAccounts: F[List[(Address, Account)]]

  def getNodes: F[List[NodeInfo]]

  def getNodeInfo(id: String): F[Option[NodeInfo]]

  def stopNode(id: String): F[Unit]

  // manage outside node
  def addNode(interface: String, port: Int): F[Option[String]]

  def deleteNode(id: String): F[Unit]
}
