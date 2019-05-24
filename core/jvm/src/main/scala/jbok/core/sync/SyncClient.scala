package jbok.core.sync

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.implicits._
import fs2._
import javax.net.ssl.SSLContext
import jbok.common.log.Logger
import jbok.core.NodeStatus
import jbok.core.api.{JbokClient, JbokClientPlatform}
import jbok.core.config.{PeerConfig, SyncConfig}
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock.SyncBlocks
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.models._
import jbok.core.peer.{Peer, PeerManager, PeerSelector}

final class SyncClient[F[_]](
    peerConfig: PeerConfig,
    syncConfig: SyncConfig,
    history: History[F],
    consensus: Consensus[F],
    executor: BlockExecutor[F],
    status: Ref[F, NodeStatus],
    peerManager: PeerManager[F],
    ssl: Option[SSLContext]
)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  def checkStatus: F[NodeStatus] =
    peerManager.connected.flatMap {
      case xs if xs.length < peerConfig.minPeers => F.pure(NodeStatus.WaitForPeers(xs.length, peerConfig.minPeers))
      case xs if xs.length >= peerConfig.minPeers =>
        for {
          current <- history.getBestBlockNumber
          td      <- history.getTotalDifficultyByNumber(current).map(_.getOrElse(BigInt(0)))
          peerOpt <- PeerSelector.bestPeer[F](td).run(xs).map(_.headOption)
          status = peerOpt match {
            case Some(peer) => NodeStatus.Syncing(peer)
            case None       => NodeStatus.Done
          }
        } yield status
    }

  val stream: Stream[F, Block] =
    Stream.eval_(log.i(s"starting Core/SyncClient")) ++
      Stream
        .eval(checkStatus)
        .evalTap(status.set)
        .flatMap {
          case _: NodeStatus.WaitForPeers     => Stream.sleep_(syncConfig.checkInterval)
          case NodeStatus.Done                => Stream.sleep_(syncConfig.checkInterval)
          case syncing: NodeStatus.Syncing[F] => Stream.eval(requestHeaders(syncing.peer)).flatMap(Stream.emits)
        }
        .repeat

  def mkClient(peer: Peer[F]): Resource[F, JbokClient[F]] =
    Resource.liftF(peer.status.get.map(_.service)).flatMap(uri => JbokClientPlatform.resource[F](uri, ssl))

  def requestHeaders(peer: Peer[F]): F[List[Block]] = mkClient(peer).use { client =>
    for {
      current <- history.getBestBlockNumber
      start = BigInt(1).max(current + 1 - syncConfig.offset)
      headers  <- client.block.getBlockHeadersByNumber(start, syncConfig.maxBlockHeadersPerRequest)
      imported <- handleBlockHeaders(peer, start, headers)
    } yield imported
  }

  private def handleBlockHeaders(peer: Peer[F], startNumber: BigInt, headers: List[BlockHeader]): F[List[Block]] =
    if (headers.isEmpty) {
      log.debug(s"got empty headers from ${peer.uri}, retry in ${syncConfig.checkInterval}").as(Nil)
    } else if (headers.headOption.map(_.number).contains(startNumber)) {
      consensus.resolveBranch(headers).flatMap {
        case Consensus.BetterBranch(newBranch) =>
          handleBetterBranch(peer, newBranch.toList)
        case Consensus.NoChainSwitch =>
          log.warn("no better branch").as(Nil)
        case Consensus.InvalidBranch =>
          log.warn(s"invalid branch").as(Nil)
      }
    } else {
      log.warn(s"block headers number do not start with request number").as(Nil)
    }

  private def handleBetterBranch(peer: Peer[F], betterBranch: List[BlockHeader]): F[List[Block]] = {
    val hashes = betterBranch.take(syncConfig.maxBlockBodiesPerRequest).map(_.hash)

    mkClient(peer).use { client =>
      for {
        bodies   <- client.block.getBlockBodies(hashes)
        imported <- handleBlockBodies(peer, betterBranch, bodies)
      } yield imported
    }
  }

  private def handleBlockBodies(peer: Peer[F], headers: List[BlockHeader], bodies: List[BlockBody]): F[List[Block]] = {
    val blocks = headers.zip(bodies).map { case (header, body) => Block(header, body) }
    executor.handleSyncBlocks(SyncBlocks(blocks, Some(peer)))
  }
}
