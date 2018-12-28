package jbok.core.sync

import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.core.config.Configs.SyncConfig
import jbok.core.consensus.Consensus
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.TypedBlock.SyncBlocks
import jbok.core.messages._
import jbok.core.models._
import jbok.core.peer.{Peer, PeerSelectStrategy}
import jbok.core.sync.SyncStatus.FullSyncing

import scala.concurrent.duration._

/**
  * [[FullSync]] should download the block headers, the block bodies
  * then import them into the local chain (validate and execute)
  */
case class FullSync[F[_]](
    config: SyncConfig,
    executor: BlockExecutor[F],
    syncStatus: Ref[F, SyncStatus]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = jbok.common.log.getLogger("FullSync")

  val peerManager = executor.peerManager

  val stream: Stream[F, Block] = {
    def checkPeer: F[Option[FullSyncing[F]]] =
      (for {
        connected <- OptionT.liftF(peerManager.connected)
        current   <- OptionT.liftF(executor.consensus.history.getBestBlockNumber)
        startNumber = BigInt(1).max(current + 1 - config.fullSyncOffset)
        bestPeer <- OptionT(PeerSelectStrategy.bestPeer[F](current + 1).run(connected).map(_.headOption))
        status   <- OptionT.liftF(bestPeer.status.get)
        syncing = FullSyncing(bestPeer, startNumber, status.bestNumber)
      } yield syncing).value

    def sync: F[List[Block]] = checkPeer.flatMap {
      case Some(fullSyncing) =>
        for {
          _      <- syncStatus.set(fullSyncing)
          synced <- requestPeer(fullSyncing)
        } yield synced

      case None =>
        for {
          _ <- syncStatus.set(SyncStatus.SyncDone)
          _ = log.info(s"no suitable peer for syncing now, retry in ${config.checkForNewBlockInterval}")
        } yield Nil
    }

    Stream.eval_(F.delay(log.info(s"start full sync"))) ++
      Stream
        .repeatEval(sync)
        .flatMap {
          case Nil    => Stream.sleep_(config.checkForNewBlockInterval) ++ Stream.empty
          case blocks => Stream.emits(blocks)
        }
        .onFinalize(F.delay(log.info("stop full sync")))
  }

  private def requestPeer(status: FullSyncing[F]): F[List[Block]] = {
    val limit = config.maxBlockHeadersPerRequest min (status.target - status.start + 1).toInt
    for {
      _     <- log.debug(s"request BlockHeader [${status.start}, ${status.start + limit})").pure[F]
      start <- T.clock.monotonic(MILLISECONDS)
      synced <- status.peer.conn
        .request(GetBlockHeaders(Left(status.start), limit, 0, false))
        .timeout(config.requestTimeout)
        .attempt
        .flatMap {
          case Right(BlockHeaders(headers, _)) =>
            T.clock.monotonic(MILLISECONDS).map { end =>
              log.debug(s"received ${headers.length} BlockHeader(s) in ${end - start}ms")
            } >> handleBlockHeaders(status.peer, status.start, headers)

          case _ =>
            F.delay(
                log.debug(s"got headers timeout from ${status.peer.id}, retry in ${config.checkForNewBlockInterval}"))
              .as(List.empty[Block])
        }
    } yield synced
  }

  private def handleBlockHeaders(peer: Peer[F], startNumber: BigInt, headers: List[BlockHeader]): F[List[Block]] =
    if (headers.isEmpty) {
      F.delay(log.debug(s"got empty headers from ${peer.id}, retry in ${config.checkForNewBlockInterval}"))
        .as(Nil)
    } else if (headers.headOption.map(_.number).contains(startNumber)) {
      executor.consensus.resolveBranch(headers).flatMap {
        case Consensus.BetterBranch(newBranch) =>
          handleBetterBranch(peer, newBranch.toList)
        case Consensus.NoChainSwitch =>
          F.delay(log.warn("no better branch")).as(Nil)
        case Consensus.InvalidBranch =>
          F.delay(log.warn(s"invalid branch")).as(Nil)
      }
    } else {
      F.delay(log.warn(s"block headers number do not start with request number")).as(Nil)
    }

  private def handleBetterBranch(peer: Peer[F], betterBranch: List[BlockHeader]): F[List[Block]] = {
    val hashes = betterBranch.take(config.maxBlockBodiesPerRequest).map(_.hash)
    log.debug(s"request BlockBody [${betterBranch.head.number}, ${betterBranch.head.number + hashes.length})")
    for {
      start <- T.clock.monotonic(MILLISECONDS)
      imported <- peer.conn.request(GetBlockBodies(hashes)).timeout(config.requestTimeout).attempt.flatMap {
        case Right(BlockBodies(bodies, _)) =>
          if (bodies.isEmpty) {
            F.delay(log.debug(s"got empty bodies from ${peer.id}, retry in ${config.checkForNewBlockInterval}"))
              .as(List.empty[Block])
          } else {
            T.clock.monotonic(MILLISECONDS).map { end =>
              log.info(s"received ${bodies.length} BlockBody(s) in ${end - start}ms")
            } >> handleBlockBodies(peer, betterBranch, bodies)
          }

        case _ =>
          F.delay(log.debug(s"got bodies timeout from ${peer.id}, retry in ${config.checkForNewBlockInterval}"))
            .as(List.empty[Block])
      }
    } yield imported
  }

  private def handleBlockBodies(peer: Peer[F], headers: List[BlockHeader], bodies: List[BlockBody]): F[List[Block]] = {
    val blocks = headers.zip(bodies).map { case (header, body) => Block(header, body) }
    executor.handleSyncBlocks(SyncBlocks(blocks, Some(peer)))
  }
}
