package jbok.core.peer
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration

trait PeerStore[F[_]] {
  type NodeId = ByteVector

  def getLastPing(id: NodeId): F[Long]

  def putLastPing(id: NodeId, ts: Long): F[Unit]

  def getLastPong(id: NodeId): F[Long]

  def putLastPong(id: NodeId, ts: Long): F[Unit]

  def getFails(id: NodeId): F[Int]

  def putFails(id: NodeId, n: Int): F[Unit]

  def getNodeOpt(id: NodeId): F[Option[PeerNode]]

  def putNode(node: PeerNode): F[Unit]

  def delNode(id: NodeId): F[Unit]

  def getNodes: F[List[PeerNode]]

  def getSeeds(n: Int, age: FiniteDuration): F[List[PeerNode]]
}
