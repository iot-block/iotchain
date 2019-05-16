package jbok.sdk.api

import jbok.core.config.Configs.CoreConfig
import jbok.core.models.SignedTransaction
import jbok.core.peer.PeerUri

trait AdminAPI[F[_]] {
  def peerUri: F[String]

  def addPeer(peerUri: String): F[Unit]

  def dropPeer(peerUri: String): F[Unit]

  def incomingPeers: F[List[PeerUri]]

  def outgoingPeers: F[List[PeerUri]]

  def pendingTransactions: F[List[SignedTransaction]]

  def getCoreConfig: F[CoreConfig]
}
