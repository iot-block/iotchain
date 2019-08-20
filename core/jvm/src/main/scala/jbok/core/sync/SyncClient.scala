package jbok.core.sync

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.implicits._
import fs2._
import javax.net.ssl.SSLContext
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.common.math.N
import jbok.core.NodeStatus
import jbok.core.api.{JbokClient, JbokClientPlatform}
import jbok.core.config.{PeerConfig, SyncConfig}
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock.SyncBlocks
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.messages.Status
import jbok.core.models._
import jbok.core.peer.{Peer, PeerManager, PeerSelector}
import jbok.network.Request

import scala.util.Random
import scala.concurrent.duration._

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
    peerManager.seedConnected.flatMap {
      case xs if xs.length < peerConfig.minPeers => F.pure(NodeStatus.WaitForPeers(xs.length, peerConfig.minPeers))
      case xs if xs.length >= peerConfig.minPeers =>
        for {
          _ <- log.i(s"checkStatus:$xs")
          current <- history.getBestBlockNumber
          td      <- history.getTotalDifficultyByNumber(current).map(_.getOrElse(N(0)))
          peerOpt <- PeerSelector.bestPeer[F](td).run(xs).map(_.headOption)
          status = peerOpt match {
            case Some(peer) => NodeStatus.Syncing(peer)
            case None       => NodeStatus.Done
          }
        } yield status
    }


  val checkSeedConnect: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/SyncClient-seedConnect")) ++ Stream.sleep_(1.minutes) ++
      Stream.eval{
        for {
          _ <- log.i(s"check seed connect")
          seeds <- peerManager.outgoing.seedDisconnects
          _ <- log.i(s"seedDisconnects:$seeds")
          _ <- Random.shuffle(seeds).headOption match {
            case Some(uri) => peerManager.outgoing.store.add(uri)
            case _ => F.unit
          }
        }yield ()
      }.flatMap(_ => Stream.sleep_(syncConfig.keepaliveInterval))
        .handleErrorWith(e => Stream.eval(log.e("SyncClient-seedConnect has an failure", e)))
        .repeat

  val statusStream: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/SyncClient-status")) ++ Stream.sleep_(3.minutes) ++
      Stream.eval{
        for {
          _ <- log.i(s"request status")
          _ <- requestStatus()
        }yield ()
      }.flatMap(_ => Stream.sleep_(syncConfig.keepaliveInterval))
        .handleErrorWith(e => Stream.eval(log.e("SyncClient-requestStatus has an failure", e)))
        .repeat

  val heartBeatStream: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/SyncClient-status")) ++ Stream.sleep_(2.minutes) ++
      Stream.eval{
        for {
          _ <- log.i(s"distribute status")
          localStatus <- peerManager.outgoing.localStatus
          message = Request.binary[F, Status](Status.name, localStatus.encoded)
          _ <- peerManager.distribute(PeerSelector.randomSelectSqrt(10), message)
        }yield ()
      }.flatMap(_ => Stream.sleep_(syncConfig.keepaliveInterval))
        .handleErrorWith(e => Stream.eval(log.e("SyncClient-status has an failure", e)))
        .repeat

  val stream: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/SyncClient")) ++
      Stream
        .eval(checkStatus)
        .evalTap(status.set)
        .flatMap {
          case _: NodeStatus.WaitForPeers     => Stream.sleep_(syncConfig.checkInterval)
          case NodeStatus.Done                => Stream.sleep_(syncConfig.checkInterval)
          case syncing: NodeStatus.Syncing[F] => Stream.eval_(requestHeaders(syncing.peer)).flatMap(Stream.emits)
        }
        .handleErrorWith(e => Stream.eval(log.e("SyncClient has an failure", e)))
        .repeat

  def mkClient(peer: Peer[F]): Resource[F, JbokClient[F]] =
    Resource.liftF(peer.status.get.map(_.service)).flatMap(uri => JbokClientPlatform.resource[F](uri, ssl))

  def requestHeaders(peer: Peer[F]): F[List[Block]] = mkClient(peer).use { client =>
    for {
      current <- history.getBestBlockNumber
      start = N(1).max(current + 1 - syncConfig.offset)
      headers  <- client.block.getBlockHeadersByNumber(start, syncConfig.maxBlockHeadersPerRequest)
      imported <- handleBlockHeaders(peer, start, headers)
    } yield imported
  }

  def requestStatus(): F[Unit] =
    for {
      peers <- peerManager.outgoing.connected.get
      seeds <- peerManager.outgoing.seedConnects
      _ <- Random.shuffle(seeds).headOption match {
        case Some(seed) if peers.get(seed).isDefined => {
          peers.get(seed) match {
            case Some(peer) => mkClient(peer._1).use{ client =>
              for {
                _ <- log.i(s"requestStatus from ${peer}")
                status <- client.block.getStatus
                _ <- log.i(s"requestStatus response ${status} from ${peer}")
                localStatus <- peerManager.outgoing.localStatus
                _ <- if (!localStatus.isCompatible(status)) {
                  log.warn(s"peer incompatible")
                }else{
                  peer._1.markStatus(status)
                }
              }yield ()
            }
            case _ => F.unit
          }
        }
        case _ => F.unit
      }
    }yield ()

  private def handleBlockHeaders(peer: Peer[F], startNumber: N, headers: List[BlockHeader]): F[List[Block]] =
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
