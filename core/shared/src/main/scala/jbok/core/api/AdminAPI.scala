package jbok.core.api

import jbok.core.config.FullConfig
import jbok.core.models.SignedTransaction
import jbok.core.peer.PeerUri
import jbok.network.rpc.PathName

@PathName("admin")
trait AdminAPI[F[_]] {
  def peerUri: F[String]

  def addPeer(peerUri: String): F[Unit]

  def dropPeer(peerUri: String): F[Unit]

  def incomingPeers: F[List[PeerUri]]

  def outgoingPeers: F[List[PeerUri]]

  def pendingTransactions: F[List[SignedTransaction]]

  def getConfig: F[FullConfig]
}
