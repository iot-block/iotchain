package jbok.core.sync

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.core.config.Configs.SyncConfig
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.TypedBlock.RequestedBlocks
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
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("FullSync")

  val peerManager = executor.peerManager

  def stream: Stream[F, Option[Unit]] = {
    val check = for {
      connected   <- peerManager.connected
      bestPeerOpt <- PeerSelectStrategy.bestPeer[F].run(connected).map(_.headOption)
      result <- bestPeerOpt match {
        case Some(peer) =>
          requestPeer(peer).map(_.some)
        case None =>
          log.debug(s"no peer for syncing now, retry in ${config.checkForNewBlockInterval}")
          none[Unit].pure[F]
      }
    } yield result

    Stream.eval(check) ++ Stream.awakeEvery[F](config.checkForNewBlockInterval).evalMap(_ => check)
  }

  private def requestPeer(peer: Peer[F]): F[Unit] =
    for {
      current <- executor.consensus.history.getBestBlockNumber
      requestNumber = current + 1

      _ = log.debug(s"request BlockHeader [${requestNumber}, ${requestNumber + config.maxBlockHeadersPerRequest}]")
      start <- T.clock.monotonic(MILLISECONDS)
      _ <- peer.conn
        .request(GetBlockHeaders(Left(requestNumber), config.maxBlockHeadersPerRequest, 0, false))
        .attempt
        .flatMap {
          case Right(BlockHeaders(headers, _)) =>
            if (headers.isEmpty) {
              F.delay(log.debug(s"got empty headers from ${peer.id}, retry in ${config.checkForNewBlockInterval}"))
            } else {
              T.clock.monotonic(MILLISECONDS).map { end =>
                log.debug(s"received ${headers.length} BlockHeader(s) in ${end - start}ms")
              } *> handleBlockHeaders(peer, current, headers)
            }
          case _ =>
            log.debug(s"got headers timeout from ${peer.id}, retry in ${config.checkForNewBlockInterval}")
            F.unit
        }
    } yield ()

  private def handleBlockHeaders(peer: Peer[F], currentNumber: BigInt, headers: List[BlockHeader]): F[Unit] =
    if (!checkHeaders(headers) || headers.last.number < currentNumber) {
      log.warn(s"got invalid block headers from ${peer.id}")
      F.unit
    } else {
      val parentIsKnown = executor.consensus.history.getBlockHeaderByHash(headers.head.parentHash).map(_.isDefined)

      parentIsKnown.ifM(
        ifTrue = {
          // find blocks with same numbers in the current chain, removing any common prefix
          for {
            blocks <- headers.map(_.number).traverse(executor.consensus.history.getBlockByNumber)
            (oldBranch, _) = blocks.flatten
              .zip(headers)
              .dropWhile {
                case (oldBlock, header) =>
                  oldBlock.header == header
              }
              .unzip
            newHeaders              = headers.dropWhile(h => oldBranch.headOption.exists(_.header.number > h.number))
            currentBranchDifficulty = oldBranch.map(_.header.difficulty).sum
            newBranchDifficulty     = newHeaders.map(_.difficulty).sum
            _ <- if (currentBranchDifficulty < newBranchDifficulty) {
              handleBetterBranch(peer, oldBranch, headers)
            } else {
              F.unit
            }
          } yield ()
        },
        ifFalse = {
          log.warn(s"the parent of the headers is unknown")
          F.unit
        }
      )
    }

  private def handleBetterBranch(peer: Peer[F], oldBranch: List[Block], headers: List[BlockHeader]): F[Unit] = {
    val transactionsToAdd = oldBranch.flatMap(_.body.transactionList)
    val hashes            = headers.take(config.maxBlockBodiesPerRequest).map(_.hash)
    log.debug(s"request BlockBody [${headers.head.number}, ${headers.head.number + hashes.length}]")
    for {
//      _ <- executor.txPool.addTransactions(transactionsToAdd)
      start <- T.clock.monotonic(MILLISECONDS)
      _ <- peer.conn.request(GetBlockBodies(hashes)).attempt.flatMap {
        case Right(BlockBodies(bodies, _)) =>
          if (bodies.isEmpty) {
            F.delay(log.debug(s"got empty bodies from ${peer.id}, retry in ${config.checkForNewBlockInterval}"))
          } else {
            T.clock.monotonic(MILLISECONDS).map { end =>
              log.info(s"received ${bodies.length} BlockBody(s) in ${end - start}ms")
            } *> handleBlockBodies(peer, headers, bodies)
          }
        case _ =>
          log.debug(s"got bodies timeout from ${peer.id}, retry in ${config.checkForNewBlockInterval}")
          F.unit
      }
    } yield ()
  }

  private def checkHeaders(headers: List[BlockHeader]): Boolean =
    if (headers.length > 1)
      headers.zip(headers.tail).forall {
        case (parent, child) =>
          parent.hash == child.parentHash && parent.number + 1 == child.number
      } else
      headers.nonEmpty

  private def handleBlockBodies(peer: Peer[F], headers: List[BlockHeader], bodies: List[BlockBody]): F[Unit] = {
    val blocks = headers.zip(bodies).map { case (header, body) => Block(header, body) }
    executor.handleRequestedBlocks(RequestedBlocks(blocks, peer))
  }
}
