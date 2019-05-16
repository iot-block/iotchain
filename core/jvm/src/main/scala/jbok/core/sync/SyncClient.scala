package jbok.core.sync

import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.core.config.Configs.SyncConfig
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock.SyncBlocks
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.messages._
import jbok.core.models._
import jbok.core.peer.{Peer, PeerManager, PeerSelector}
import jbok.network.{Request, Response}
import org.http4s.client.Client

final class SyncClient[F[_]](
    config: SyncConfig,
    history: History[F],
    consensus: Consensus[F],
    executor: BlockExecutor[F],
    syncStatus: Ref[F, SyncStatus],
    peerManager: PeerManager[F],
    client: Client[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  def choosePeer: F[Option[Peer[F]]] =
    (for {
      connected <- OptionT.liftF(peerManager.connected)
      current   <- OptionT.liftF(history.getBestBlockNumber)
      bestPeer  <- OptionT(PeerSelector.bestPeer[F](current + 1).run(connected).map(_.headOption))
    } yield bestPeer).value

  val stream: Stream[F, Block] = {
    Stream
      .eval(choosePeer)
      .flatMap {
        case Some(peer) => Stream.eval(requestHeaders(peer)).flatMap(Stream.emits)
        case None       => Stream.sleep_(config.checkForNewBlockInterval)
      }
      .repeat
  }

  def requestHeaders(peer: Peer[F]): F[List[Block]] =
    for {
      current <- history.getBestBlockNumber
      start = BigInt(1).max(current + 1 - config.fullSyncOffset)
      _ <- log.debug(s"request BlockHeader from number=$start")
      request = Request.binary[F, GetBlockHeadersByNumber](GetBlockHeadersByNumber.name, GetBlockHeadersByNumber(start, config.maxBlockHeadersPerRequest).asValidBytes)
      response <- client.fetch(Request.toHttp4s(request))(resp => Response.fromHttp4s(resp))
      headers  <- response.as[BlockHeaders]
      imported <- handleBlockHeaders(peer, start, headers.headers)
    } yield imported

  private def handleBlockHeaders(peer: Peer[F], startNumber: BigInt, headers: List[BlockHeader]): F[List[Block]] =
    if (headers.isEmpty) {
      log.debug(s"got empty headers from ${peer.id}, retry in ${config.checkForNewBlockInterval}").as(Nil)
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
    val hashes = betterBranch.take(config.maxBlockBodiesPerRequest).map(_.hash)

    betterBranch match {
      case head :: _ => log.debug(s"request BlockBody [${head.number}, ${head.number + hashes.length})")
      case Nil       => ()
    }

    for {
      request     <- Request.binary[F, GetBlockBodies](GetBlockBodies.name, GetBlockBodies(hashes).asValidBytes).pure[F]
      response    <- client.fetch(Request.toHttp4s(request))(resp => Response.fromHttp4s(resp))
      blockBodies <- response.as[BlockBodies]
      imported    <- handleBlockBodies(peer, betterBranch, blockBodies.bodies)
    } yield imported
  }

  private def handleBlockBodies(peer: Peer[F], headers: List[BlockHeader], bodies: List[BlockBody]): F[List[Block]] = {
    val blocks = headers.zip(bodies).map { case (header, body) => Block(header, body) }
    executor.handleSyncBlocks(SyncBlocks(blocks, Some(peer)))
  }
}
