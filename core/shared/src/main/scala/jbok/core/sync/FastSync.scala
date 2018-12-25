package jbok.core.sync

import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.InspectableQueue
import jbok.codec.rlp.implicits._
import jbok.common._
import jbok.core.config.Configs.SyncConfig
import jbok.core.messages._
import jbok.core.models._
import jbok.core.peer.{Peer, PeerManager}
import jbok.core.sync.NodeHash.{EvmCodeHash, StateMptNodeHash, StorageMptNodeHash}
import jbok.crypto._
import jbok.crypto.authds.mpt.MptNode
import scodec.Codec
import scodec.bits.ByteVector

import scala.util.Random

/**
  * [[FastSync]] should download the [[BlockHeader]] and corresponding [[BlockBody]] and [[Receipt]]
  * plus
  *   - the world Merkle Patricia Trie under the root hash of the `pivot` height
  *   - for every [[Account]] in the trie, download its storage node and code if exists
  *
  * all remaining recent blocks, delegate to [[FullSync]]
  */
final class FastSync[F[_]](
    config: SyncConfig,
    peerManager: PeerManager[F],
    bodyQueue: InspectableQueue[F, ByteVector],
    receiptQueue: InspectableQueue[F, ByteVector],
    nodeQueue: InspectableQueue[F, NodeHash],
    jobQueue: InspectableQueue[F, Option[Message]],
    running: Ref[F, Int]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = jbok.common.log.getLogger("FastSync")

  import FastSync._
  import config._

  private[this] val history = peerManager.history

  val stream: Stream[F, Unit] = {
    Stream.eval(F.delay(log.info(s"start fast sync"))) ++ Stream
      .eval(initState)
      .flatMap {
        case Some(state) => startFastSync(state)
        case None        => Stream.empty
      }
      .onFinalize(F.delay(s"finish fast sync"))
  }

  private def initState: F[Option[FastSyncState[F]]] =
    for {
      peersStatus <- getPeersStatus
      bestNumber  <- history.getBestBlockNumber
      bestPeerNumber = peersStatus.map(_._2.bestNumber).max
      stateOpt <- if (bestPeerNumber <= bestNumber + config.fastSyncOffset) {
        log.debug(s"skip because best peer number ${bestPeerNumber} - ${config.fastSyncOffset} <= ${bestNumber}")
        None.pure[F]
      } else {
        for {
          peersHeader <- getPeersHeader(peersStatus)
          stateOpt    <- chooseTargetHeader(peersHeader)
        } yield stateOpt
      }
    } yield stateOpt

  private def startFastSync(state: FastSyncState[F]): Stream[F, Unit] =
    for {
      bestBlockNumber <- Stream.eval(history.getBestBlockNumber)
      _               <- Stream.eval(nodeQueue.enqueue1(StateMptNodeHash(state.target.stateRoot)))
      _ <- if (bestBlockNumber >= state.target.number) {
        Stream.eval(F.delay(log.info("fast sync finished, switching to full mode")))
      } else {
        val getNode =
          nodeQueue.dequeue
            .chunkLimit(maxNodesPerRequest)
            .map(chunk => GetNodeData(chunk.toList))

        val getBody =
          bodyQueue.dequeue
            .chunkLimit(maxBlockBodiesPerRequest)
            .map(chunk => GetBlockBodies(chunk.toList))

        val getReceipt =
          receiptQueue.dequeue.chunkLimit(maxReceiptsPerRequest).map(chunk => GetReceipts(chunk.toList))

        val getHeader = {
          val nextBlockNumber = bestBlockNumber + 1
          val numRequests =
            math
              .ceil((state.target.number - nextBlockNumber).toDouble / maxBlockHeadersPerRequest.toDouble)
              .toInt

          Stream
            .emits(
              (0 until numRequests).map { i =>
                val start = nextBlockNumber + i * maxBlockHeadersPerRequest
                val limit =
                  maxBlockHeadersPerRequest min (state.target.number - start + 1).toInt
                GetBlockHeaders(
                  Left(start),
                  limit,
                  0,
                  false
                )
              }
            )
            .covary[F]
        }

        val enqueue =
          Stream(
            getNode,
            getBody,
            getReceipt,
            getHeader
          ).map(_.covaryOutput[Message]).parJoinUnbounded.map(_.some) to jobQueue.enqueue

        download(state.goodPeers, jobQueue.dequeue.unNoneTerminate)
          .concurrently(enqueue)
          .evalMap(_ => history.putBestBlockNumber(state.target.number))
      }
    } yield ()

  /**
    * ask at least [[minPeersToChooseTargetBlock]] peers
    * for our next unknown [[BlockHeader]]
    */
  private def getPeersStatus: F[List[(Peer[F], Status)]] = {
    def go(peers: List[Peer[F]]): F[List[(Peer[F], Status)]] =
      if (peers.length >= minPeersToChooseTargetBlock) {
        peers.map(peer => peer.status.get.map(status => peer -> status)).sequence
      } else {
        log.warn(s"fast sync did not started, peers not enough ${peers.length}/${minPeersToChooseTargetBlock}")
        T.sleep(retryInterval) >> getPeersStatus
      }

    peerManager.connected >>= go
  }

  private def getPeersHeader(peersStatus: List[(Peer[F], Status)]) = {
    log.trace(s"asking ${peersStatus.length} peers for block headers")
    peersStatus
      .traverse {
        case (peer, status) =>
          requestBlockHeaders(peer, status.bestNumber, 1)
            .map(headers => peer -> headers.head)
            .attemptT
            .toOption
            .value
      }
      .map(_.flatten)
  }

  private def randomPeer(peers: List[Peer[F]]): F[Peer[F]] =
    peerManager.connected.map { connected =>
      val available = connected.filter(p => peers.contains(p))
      Random.shuffle(available).head
    }

  /**
    * download with [[maxConcurrentRequests]]
    *
    * should block when no more concurrency or available peer
    */
  private def download(peers: List[Peer[F]], requests: Stream[F, Message]): Stream[F, Unit] =
    requests
      .map { request =>
        for {
          _    <- Stream.eval(running.update(_ + 1))
          peer <- Stream.eval(randomPeer(peers))
          _ <- Stream.eval(request match {
            case req: GetBlockHeaders => handleBlockHeaders(peer, req)
            case req: GetBlockBodies  => handleBlockBodies(peer, req)
            case req: GetNodeData     => handleNodeData(peer, req)
            case req: GetReceipts     => handleReceipts(peer, req)
            case _                    => F.unit
          })
          _ <- Stream.eval(running.update(_ - 1))
          _ <- Stream.eval(isDone.ifM(jobQueue.enqueue1(None), F.unit))
        } yield ()
      }
      .parJoin(maxConcurrentRequests)

  private def isDone: F[Boolean] =
    jobQueue.getSize.map(_ == 0) &&
      bodyQueue.getSize.map(_ == 0) &&
      receiptQueue.getSize.map(_ == 0) &&
      nodeQueue.getSize.map(_ == 0) &&
      running.get.map(_ == 0)

  /**
    * choose a median number of all *active* peers as our target number
    * make sure we have enough peers that have the same stateRoot for that target number
    */
  private def chooseTargetHeader(received: List[(Peer[F], BlockHeader)]): F[Option[FastSyncState[F]]] =
    if (received.size >= minPeersToChooseTargetBlock) {
      val (_, chosenBlockHeader) = chooseMedian(received)
      val targetBlockNumber      = BigInt(0).max(chosenBlockHeader.number - fastSyncOffset)
      val targetHeaders = received.traverse {
        case (peer, _) =>
          requestBlockHeaders(peer, targetBlockNumber, 1).map { headers =>
            headers.find(_.number == targetBlockNumber) match {
              case Some(targetBlockHeader) =>
                Some(peer -> targetBlockHeader)
              case _ =>
                None
            }
          }
      }

      for {
        headers <- targetHeaders
        (_, peerToBlockHeader) = headers.flatten.groupBy(_._2.stateRoot).maxBy(_._2.size)
        nSameHeadersRequired   = math.min(minPeersToChooseTargetBlock, 3)
        state = if (peerToBlockHeader.length >= nSameHeadersRequired) {
          val (goodPeers, blockHeaders) = peerToBlockHeader.unzip
          val targetBlockHeader         = blockHeaders.head
          log.debug(
            s"got enough block headers that have the same stateRoot ${peerToBlockHeader.length}/${nSameHeadersRequired}")

          FastSyncState(targetBlockHeader, goodPeers).some
        } else {
          log.debug(s"""could not get enough block headers that have the same stateRoot
               |requires ${nSameHeadersRequired}, but only found ${peerToBlockHeader.size}""".stripMargin)
          None
        }
      } yield state

    } else {
      log.info(s"fast sync did not started, headers ${received.length}/${minPeersToChooseTargetBlock} not enough")
      F.pure(None)
    }

  /** choose the peer with median height **/
  private def chooseMedian(received: List[(Peer[F], BlockHeader)]) = {
    val headers = received.sortBy(_._2.number)
    headers(headers.length / 2)
  }

  private def requestBlockHeaders(peer: Peer[F], start: BigInt, limit: Int): F[List[BlockHeader]] = {
    log.trace(s"request block headers from ${peer.id}")
    val request = GetBlockHeaders(Left(start), limit, 0, false)
    for {
      response <- peer.conn.request(request).map(_.asInstanceOf[BlockHeaders]).timeout(config.requestTimeout)
    } yield response.headers
  }

  /**
    * persist received [[BlockHeaders]] and
    * start corresponding [[GetBlockBodies]] and [[GetReceipts]] request
    */
  private def handleBlockHeaders(peer: Peer[F], request: GetBlockHeaders): F[Unit] =
    peer.conn
      .request(request)
      .timeout(config.requestTimeout)
      .attempt
      .flatMap {
        case Right(res) =>
          val response = res.asInstanceOf[BlockHeaders]
          log.debug(s"downloaded ${response.headers.length} BlockHeader(s) from ${peer.id}")

          val blockHashes = response.headers.map(_.hash)

          if (isHeadersConsistent(response.headers) && response.headers.length == request.maxHeaders) {
            (Stream
              .emits(response.headers)
              .evalMap(header => history.putBlockHeader(header, updateTD = true)) ++
              Stream
                .emits(blockHashes)
                .to(bodyQueue.enqueue) ++
              Stream
                .emits(blockHashes)
                .to(receiptQueue.enqueue)).compile.drain
          } else {
            // TODO we should consider ban peer here
            F.unit
          }

        case Left(e) =>
          log.warn("handle block headers error", e)
          jobQueue.enqueue1(request.some)
      }

  /**
    * persist received [[BlockBodies]] and
    * enqueue all remaining request into the [[bodyQueue]]
    */
  private[jbok] def handleBlockBodies(peer: Peer[F], request: GetBlockBodies): F[Unit] =
    peer.conn
      .request(request)
      .timeout(config.requestTimeout)
      .attempt
      .flatMap {
        case Right(res) =>
          val response = res.asInstanceOf[BlockBodies]
          log.debug(s"downloaded ${response.bodies.length} BlockBody(s) from ${peer.id}")
          val receivedBodies  = response.bodies
          val receivedHashes  = request.hashes.take(receivedBodies.size)
          val remainingHashes = request.hashes.drop(receivedBodies.size)

          (Stream
            .emits(receivedHashes.zip(receivedBodies))
            .evalMap { case (hash, body) => history.putBlockBody(hash, body) } ++
            Stream
              .emits(remainingHashes)
              .to(bodyQueue.enqueue)).compile.drain

        case Left(e) =>
          log.warn("handle block bodies error", e)
          jobQueue.enqueue1(Some(request))
      }

  /**
    * persist received [[Receipts]] and
    * enqueue all remaining request into the [[receiptQueue]]
    */
  private[jbok] def handleReceipts(peer: Peer[F], request: GetReceipts): F[Unit] =
    peer.conn
      .request(request)
      .timeout(config.requestTimeout)
      .attempt
      .flatMap {
        case Right(res) =>
          val response = res.asInstanceOf[Receipts]
          log.debug(s"downloaded ${response.receiptsForBlocks.length} Receipt(s) from ${peer.id}")
          val receivedReceipts = response.receiptsForBlocks
          val receivedHashes   = request.blockHashes.take(receivedReceipts.size)
          val remainingHashes  = request.blockHashes.drop(receivedReceipts.size)

          (Stream
            .emits(receivedHashes.zip(receivedReceipts))
            .evalMap { case (hash, receipts) => history.putReceipts(hash, receipts) } ++
            Stream
              .emits(remainingHashes)
              .to(receiptQueue.enqueue)).compile.drain

        case Left(e) =>
          log.warn("handle receipts error", e)
          jobQueue.enqueue1(Some(request))
      }

  /**
    * persist received `Accounts Node`, `Storage Node` and `Code` and
    * enqueue all unfinished work into the [[nodeQueue]]
    */
  private[jbok] def handleNodeData(peer: Peer[F], request: GetNodeData): F[Unit] = {
    peer.conn
      .request(request)
      .timeout(config.requestTimeout)
      .attempt
      .flatMap {
        case Right(res) =>
          val response = res.asInstanceOf[NodeData]
          log.debug(s"downloaded ${response.values.length} NodeData from ${peer.id}")
          val requested = request.nodeHashes.map(x => x.v -> x).toMap
          val (receivedNodeHashes, childrenHashes, receivedAccounts, receivedStorages, receivedEvmCodes) =
            response.values.foldLeft(
              (Set[NodeHash](),
               List[NodeHash](),
               List[(NodeHash, ByteVector)](),
               List[(NodeHash, ByteVector)](),
               List[(NodeHash, ByteVector)]())) {
              case ((receivedHashes, childHashes, receivedAccounts, receivedStorages, receivedEvmCodes), value) =>
                val receivedHash = value.kec256
                requested.get(receivedHash) match {
                  case None =>
                    (receivedHashes, childHashes, receivedAccounts, receivedStorages, receivedEvmCodes)

                  case Some(x: StateMptNodeHash) =>
                    val node: MptNode = RlpCodec.decode[MptNode](value.bits).require.value
                    val hashes = node match {
                      case MptNode.LeafNode(_, value) =>
                        val account = RlpCodec.decode[Account](value.bits).require.value
                        val codeHash = account.codeHash match {
                          case Account.EmptyCodeHash => Nil
                          case hash                  => EvmCodeHash(hash) :: Nil
                        }
                        val storageHash = account.storageRoot match {
                          case Account.EmptyStorageRootHash => Nil
                          case hash                         => StorageMptNodeHash(hash) :: Nil
                        }
                        codeHash ++ storageHash

                      case MptNode.BranchNode(branches, _) =>
                        branches.collect {
                          case Some(Left(hash)) => StateMptNodeHash(hash)
                        }

                      case MptNode.ExtensionNode(_, child) =>
                        child match {
                          case Left(hash) => StateMptNodeHash(hash) :: Nil
                          case Right(_)   => Nil
                        }
                    }
                    (receivedHashes + x,
                     childHashes ++ hashes,
                     (x, value) :: receivedAccounts,
                     receivedStorages,
                     receivedEvmCodes)

                  case Some(x: StorageMptNodeHash) =>
                    val node: MptNode = RlpCodec.decode[MptNode](value.bits).require.value
                    val hashes = node match {
                      case MptNode.LeafNode(_, _) =>
                        Nil

                      case MptNode.BranchNode(branches, _) =>
                        branches.collect {
                          case Some(Left(hash)) => StorageMptNodeHash(hash)
                        }

                      case MptNode.ExtensionNode(_, child) =>
                        child match {
                          case Left(hash) => StorageMptNodeHash(hash) :: Nil
                          case Right(_)   => Nil
                        }
                    }
                    (receivedHashes + x,
                     childHashes ++ hashes,
                     receivedAccounts,
                     (x, value) :: receivedStorages,
                     receivedEvmCodes)

                  case Some(x: EvmCodeHash) =>
                    (receivedHashes + x,
                     childHashes,
                     receivedAccounts,
                     receivedStorages,
                     (x, value) :: receivedEvmCodes)
                }
            }

          val remainingHashes = request.nodeHashes.filterNot(receivedNodeHashes.contains)

          (Stream
            .emits(receivedAccounts)
            .covary[F]
            .evalMap { case (hash, bytes) => history.putMptNode(hash.v, bytes) } ++
            Stream
              .emits(receivedStorages)
              .covary[F]
              .evalMap { case (hash, bytes) => history.putMptNode(hash.v, bytes) } ++
            Stream
              .emits(receivedEvmCodes)
              .covary[F]
              .evalMap { case (hash, code) => history.putCode(hash.v, code) } ++
            Stream
              .emits(remainingHashes ++ childrenHashes)
              .covary[F]
              .to(nodeQueue.enqueue)).compile.drain

        case _ =>
          jobQueue.enqueue1(Some(request))
      }
  }

  private def isHeadersConsistent(headers: List[BlockHeader]): Boolean =
    if (headers.length > 1) {
      headers.zip(headers.tail).forall {
        case (parent, child) => parent.hash == child.parentHash && parent.number + 1 == child.number
      }
    } else {
      true
    }
}

object FastSync {
  case class FastSyncState[F[_]](target: BlockHeader, goodPeers: List[Peer[F]])

  def apply[F[_]](config: SyncConfig, pm: PeerManager[F], maxQueueSize: Int = 1024)(implicit F: ConcurrentEffect[F],
                                                                                    T: Timer[F]): F[FastSync[F]] =
    for {
      bodyQueue    <- InspectableQueue.bounded[F, ByteVector](maxQueueSize)
      receiptQueue <- InspectableQueue.bounded[F, ByteVector](maxQueueSize)
      nodeQueue    <- InspectableQueue.bounded[F, NodeHash](maxQueueSize)
      jobQueue     <- InspectableQueue.bounded[F, Option[Message]](maxQueueSize)
      running      <- Ref.of[F, Int](0)
    } yield new FastSync[F](config, pm, bodyQueue, receiptQueue, nodeQueue, jobQueue, running)
}
