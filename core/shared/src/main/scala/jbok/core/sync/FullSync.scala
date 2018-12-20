package jbok.core.sync

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

  val stream: Stream[F, Option[Unit]] = {
    val check = for {
      connected <- peerManager.connected
      current   <- executor.consensus.history.getBestBlockNumber
      startNumber = BigInt(1).max(current + 1 - config.fullSyncOffset)
      bestPeerOpt <- PeerSelectStrategy.bestPeer[F](startNumber).run(connected).map(_.headOption)
      result <- bestPeerOpt match {
        case Some(peer) =>
          for {
            _      <- syncStatus.set(SyncStatus.FullSyncing(startNumber))
            result <- requestPeer(peer, startNumber).map(_.some)
          } yield result

        case None =>
          log.debug(s"no suitable peer for syncing now, retry in ${config.checkForNewBlockInterval}")
          none[Unit].pure[F]
      }
    } yield result

    Stream.eval {
      F.delay(log.info(s"start full sync")) >> check
    } ++ Stream.awakeEvery[F](config.checkForNewBlockInterval).evalMap(_ => check)
  }

  private def requestPeer(peer: Peer[F], startNumber: BigInt): F[Unit] =
    for {
      status <- peer.status.get
      limit = config.maxBlockHeadersPerRequest min (status.bestNumber - startNumber + 1).toInt
      _     = log.debug(s"request BlockHeader [${startNumber}, ${startNumber + limit})")
      start <- T.clock.monotonic(MILLISECONDS)
      _ <- peer.conn
        .request(GetBlockHeaders(Left(startNumber), limit, 0, false))
        .timeout(config.requestTimeout)
        .attempt
        .flatMap {
          case Right(BlockHeaders(headers, _)) =>
            if (headers.isEmpty) {
              F.delay(log.debug(s"got empty headers from ${peer.id}, retry in ${config.checkForNewBlockInterval}"))
            } else {
              T.clock.monotonic(MILLISECONDS).map { end =>
                log.debug(s"received ${headers.length} BlockHeader(s) in ${end - start}ms")
              } >> handleBlockHeaders(peer, startNumber, headers)
            }
          case _ =>
            log.debug(s"got headers timeout from ${peer.id}, retry in ${config.checkForNewBlockInterval}")
            F.unit
        }
    } yield ()

  private def handleBlockHeaders(peer: Peer[F], startNumber: BigInt, headers: List[BlockHeader]): F[Unit] =
    if (headers.headOption.map(_.number).contains(startNumber)) {
      executor.consensus.resolveBranch(headers).flatMap {
        case Consensus.BetterBranch(newBranch) =>
          handleBetterBranch(peer, newBranch.toList)
        case Consensus.NoChainSwitch =>
          F.delay(log.warn("no better branch"))
        case Consensus.InvalidBranch =>
          F.delay(log.warn(s"invalid branch"))
      }
    } else {
      F.delay(log.warn(s"block headers number do not start with request number"))
    }

  private def handleBetterBranch(peer: Peer[F], betterBranch: List[BlockHeader]): F[Unit] = {
    val hashes = betterBranch.take(config.maxBlockBodiesPerRequest).map(_.hash)
    log.debug(s"request BlockBody [${betterBranch.head.number}, ${betterBranch.head.number + hashes.length})")
    for {
      start <- T.clock.monotonic(MILLISECONDS)
      _ <- peer.conn.request(GetBlockBodies(hashes)).timeout(config.requestTimeout).attempt.flatMap {
        case Right(BlockBodies(bodies, _)) =>
          if (bodies.isEmpty) {
            F.delay(log.debug(s"got empty bodies from ${peer.id}, retry in ${config.checkForNewBlockInterval}"))
          } else {
            T.clock.monotonic(MILLISECONDS).map { end =>
              log.info(s"received ${bodies.length} BlockBody(s) in ${end - start}ms")
            } >> handleBlockBodies(peer, betterBranch, bodies)
          }
        case _ =>
          log.debug(s"got bodies timeout from ${peer.id}, retry in ${config.checkForNewBlockInterval}")
          F.unit
      }
    } yield ()
  }

  private def handleBlockBodies(peer: Peer[F], headers: List[BlockHeader], bodies: List[BlockBody]): F[Unit] = {
    val blocks = headers.zip(bodies).map { case (header, body) => Block(header, body) }
    executor.handleSyncBlocks(SyncBlocks(blocks, Some(peer)))
  }
}
