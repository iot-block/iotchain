package jbok.app.service

import cats.effect.Sync
import cats.implicits._
import jbok.app.service.store.{BlockStore, TransactionStore}
import jbok.common.log.Logger
import jbok.common.math.N
import jbok.core.CoreNode
import jbok.core.config.FullConfig
import jbok.core.models.SignedTransaction
import jbok.core.peer.PeerUri
import jbok.core.api.AdminAPI

final class AdminService[F[_]](
    core: CoreNode[F],
    blockStore: BlockStore[F],
    txStore: TransactionStore[F]
)(implicit F: Sync[F]) extends AdminAPI[F] {
  private[this] val log = Logger[F]

  override def peerUri: F[String] =
    core.peerManager.incoming.localPeerUri.map(_.toString)

  override def addPeer(peerUri: String): F[Unit] =
    for {
      peerUri <- F.fromEither(PeerUri.fromStr(peerUri))
      _       <- log.info(s"add a peer: ${peerUri}")
      _       <- core.peerManager.outgoing.store.add(peerUri)
    } yield ()

  override def dropPeer(peerUri: String): F[Unit] =
    for {
      peerUri <- F.fromEither(PeerUri.fromStr(peerUri))
      _       <- log.info(s"drop a peer: ${peerUri}")
      _       <- core.peerManager.close(peerUri)
    } yield ()

  override def incomingPeers: F[List[PeerUri]] =
    core.peerManager.incoming.connected.get.map(_.values.map(_._1.uri).toList)

  override def outgoingPeers: F[List[PeerUri]] =
    core.peerManager.outgoing.connected.get.map(_.values.map(_._1.uri).toList)

  override def pendingTransactions: F[List[SignedTransaction]] =
    core.txPool.getPendingTransactions.map(_.keys.toList)

  override def getConfig: F[FullConfig] =
    F.pure(core.config)

  override def deleteBlockUntil(number: N): F[Unit] = {
    def delBlock(n: N, from: N): F[Unit] = {
      if (from > n) core.history.getBlockHeaderByNumber(from).flatMap{
        case Some(block) => for {
          _ <- core.history.delBlock(block.hash)
          _ <- delBlock(n, from-1)
        }yield ()
        case _ => F.unit
      }
      else F.unit
    }

    for {
      best   <- core.history.getBestBlock
      bestNumber = best.header.number
      _ <- List.range((number+1).toBigInt, (bestNumber + 1).toBigInt).traverse_ { n =>
        blockStore.delByBlockNumber(n) >> txStore.delByBlockNumber(n)}
      _ <- delBlock(number, bestNumber)
      _ <- core.history.putBestBlockNumber(number)
    }yield ()

  }
}
