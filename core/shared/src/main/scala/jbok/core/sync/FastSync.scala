package jbok.core.sync

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.{InspectableQueue, SignallingRef}
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.common.concurrent.PriorityQueue
import jbok.core.config.Configs.SyncConfig
import jbok.core.messages._
import jbok.core.models._
import jbok.core.peer.{Peer, PeerManager}
import jbok.core.sync.NodeHash.{ContractStorageMptNodeHash, EvmCodeHash, StateMptNodeHash, StorageRootHash}
import jbok.crypto._
import jbok.crypto.authds.mpt.MptNode
import scodec.bits.ByteVector

import scala.util.Random

final case class FastSyncState(targetBlockHeader: BlockHeader)

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
    stateRef: Ref[F, FastSyncState],
    bodyQueue: InspectableQueue[F, Option[ByteVector]],
    receiptQueue: InspectableQueue[F, Option[ByteVector]],
    nodeQueue: InspectableQueue[F, Option[NodeHash]],
    jobQueue: PriorityQueue[F, Option[Message]],
    done: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("FastSync")

  import config._

  private[this] val history = peerManager.history

  def stream: Stream[F, Unit] =
    for {
      received <- Stream.eval(getPeersStatus)
      _ = log.debug(s"got peer best header list ${received.map(_._2.tag)}")
      state <- Stream.eval(initState(received))
      _ = log.debug(s"choose target ${state.targetBlockHeader.tag}")
      _ <- Stream.eval(stateRef.set(state))
      _ <- startFastSync(state)
    } yield ()

  private[jbok] def startFastSync(state: FastSyncState): Stream[F, Unit] =
    for {
      bestBlockNumber <- Stream.eval(history.getBestBlockNumber)
      _ <- if (bestBlockNumber >= state.targetBlockHeader.number) {
        log.info("fast sync finished, switching to full mode")
        Stream.eval(done.set(true))
      } else {
        val getNode =
          nodeQueue.dequeue.unNoneTerminate.chunkLimit(maxNodesPerRequest).map(chunk => GetNodeData(chunk.toList))

        val getBody =
          bodyQueue.dequeue.unNoneTerminate
            .chunkLimit(maxBlockBodiesPerRequest)
            .map(chunk => GetBlockBodies(chunk.toList))

        val getReceipt =
          receiptQueue.dequeue.unNoneTerminate.chunkLimit(maxReceiptsPerRequest).map(chunk => GetReceipts(chunk.toList))

        val getHeader = {
          val limit = math.min(maxBlockHeadersPerRequest, (state.targetBlockHeader.number - bestBlockNumber).toInt)
          val req   = GetBlockHeaders(Left(bestBlockNumber + 1), limit, 0, false)
          Stream(req).covary[F]
        }

        val enqueue =
          Stream(
            getNode.map(_.some    -> 2),
            getBody.map(_.some    -> 1),
            getReceipt.map(_.some -> 1),
            getHeader.map(_.some  -> 1)).parJoinUnbounded ++ Stream(None -> Int.MinValue).covary[F] to jobQueue.enqueue

        Stream(download(jobQueue.dequeue.unNoneTerminate), enqueue).parJoinUnbounded
      }
    } yield ()

  /**
    * ask at least [[minPeersToChooseTargetBlock]] peers
    * for our next unknown [[BlockHeader]]
    */
  private[jbok] def getPeersStatus: F[List[(Peer[F], BlockHeader)]] = {
    def go(peers: List[Peer[F]]) =
      if (peers.length >= minPeersToChooseTargetBlock) {
        log.trace(s"asking ${peers.length} peers for block headers")
        peers
          .traverse { peer =>
            peer.status.get.map(_.bestNumber).flatMap { bestNumber =>
              requestBlockHeaders(peer, bestNumber, 1)
                .map(headers => peer -> headers.head)
                .attemptT
                .toOption
                .value
            }
          }
          .map(_.flatten)
      } else {
        log.warn(s"fast sync did not started , peers not enough ${peers.length}/${minPeersToChooseTargetBlock}")
        T.sleep(retryInterval) *> getPeersStatus
      }

    peerManager.connected >>= go
  }

  private[jbok] def randomPeer: F[Peer[F]] =
    peerManager.connected.map(peers => Random.shuffle(peers).head)

  /**
    * download with [[maxConcurrentRequests]]
    *
    * should block when no more concurrency or available peer
    */
  private[jbok] def download(requests: Stream[F, Message]): Stream[F, Unit] =
    requests
      .map { request =>
        for {
          peer <- Stream.eval(randomPeer)
          _ <- Stream.eval(request match {
            case req: GetBlockBodies  => handleBlockBodies(peer, req)
            case req: GetBlockHeaders => handleBlockHeaders(peer, req)
            case req: GetNodeData     => handleNodeData(peer, req)
            case req: GetReceipts     => handleReceipts(peer, req)
            case _                    => F.unit
          })
        } yield ()
      }
      .parJoin(maxConcurrentRequests)

  /**
    * choose a median number of all *active* peers as our target number
    * make sure we have enough peers that have the same stateRoot for that target number
    */
  private[jbok] def initState(received: List[(Peer[F], BlockHeader)]): F[FastSyncState] =
    if (received.size >= minPeersToChooseTargetBlock) {
      val (_, chosenBlockHeader) = chooseTargetBlock(received)
      val targetBlockNumber      = BigInt(0).max(chosenBlockHeader.number - targetBlockOffset)
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
        state <- if (peerToBlockHeader.length >= nSameHeadersRequired) {
          val peers                     = received.map(_._1).toSet
          val (goodPeers, blockHeaders) = peerToBlockHeader.unzip
          val targetBlockHeader         = blockHeaders.head
          log.debug(
            s"got enough block headers that have the same stateRoot ${peerToBlockHeader.length}/${nSameHeadersRequired}")
          nodeQueue
            .enqueue1(StateMptNodeHash(targetBlockHeader.stateRoot).some)
            .map(_ => FastSyncState(targetBlockHeader))
        } else {
          log.debug(s"""could not get enough block headers that have the same stateRoot
               |requires ${nSameHeadersRequired}, but only found ${peerToBlockHeader.size}""".stripMargin)
          T.sleep(retryInterval) *> getPeersStatus >>= initState
        }
      } yield state

    } else {
      log.info(s"fast sync did not started, headers ${received.length}/${minPeersToChooseTargetBlock} not enough")
      T.sleep(retryInterval) *> getPeersStatus >>= initState
    }

  /** choose the peer with median height **/
  private[jbok] def chooseTargetBlock(received: List[(Peer[F], BlockHeader)]) = {
    val headers = received.sortBy(_._2.number)
    headers(headers.length / 2)
  }

  private[jbok] def requestBlockHeaders(peer: Peer[F], start: BigInt, limit: Int): F[List[BlockHeader]] = {
    log.trace(s"request block headers from ${peer.id}")
    val request = GetBlockHeaders(Left(start), limit, 0, false)
    for {
      response <- peer.conn.request(request).map(_.asInstanceOf[BlockHeaders])
    } yield response.headers
  }

  /**
    * persist received [[BlockHeaders]] and
    * start corresponding [[GetBlockBodies]] and [[GetReceipts]] request
    */
  private[jbok] def handleBlockHeaders(peer: Peer[F], request: GetBlockHeaders): F[Unit] =
    peer.conn
      .request(request)
      .flatMap { res =>
        val response = res.asInstanceOf[BlockHeaders]
        log.debug(s"downloaded ${response.headers.length} BlockHeader(s) from ${peer.id}")

        val blockHashes = response.headers.map(_.hash)

        if (isHeadersConsistent(response.headers)) {
          Stream
            .emits(response.headers)
            .evalMap(history.putBlockHeader)
            .compile
            .drain *>
            Stream
              .emits(blockHashes)
              .map(_.some)
              .to(bodyQueue.enqueue)
              .compile
              .drain *>
            Stream
              .emits(blockHashes)
              .map(_.some)
              .to(receiptQueue.enqueue)
              .compile
              .drain *>
            stateRef.get.map(x => blockHashes.contains(x.targetBlockHeader.hash)).ifM(
              ifTrue = F.delay(log.debug("finish downloading headers")),
              ifFalse = F.unit
            )
        } else {
          // TODO we should consider ban peer here
          F.unit
        }
      }

  /**
    * persist received [[BlockBodies]] and
    * enqueue all remaining request into the [[bodyQueue]]
    */
  private[jbok] def handleBlockBodies(peer: Peer[F], request: GetBlockBodies): F[Unit] =
    peer.conn
      .request(request)
      .flatMap { res =>
        val response = res.asInstanceOf[BlockBodies]
        log.debug(s"downloaded ${response.bodies.length} BlockBody(s) from ${peer.id}")
        val receivedBodies  = response.bodies
        val receivedHashes  = request.hashes.take(receivedBodies.size)
        val remainingHashes = request.hashes.drop(receivedBodies.size)

        Stream
          .emits(receivedHashes.zip(receivedBodies))
          .evalMap { case (hash, body) => history.putBlockBody(hash, body) }
          .compile
          .drain *>
          Stream
            .emits(remainingHashes)
            .map(_.some)
            .to(bodyQueue.enqueue)
            .compile
            .drain *>
          stateRef.get
            .map(x => receivedHashes.contains(x.targetBlockHeader.hash))
            .ifM(
              ifTrue = {
                log.debug("finish downloading bodies")
                bodyQueue.enqueue1(None)
              },
              ifFalse = F.unit
            )
      }

  /**
    * persist received [[Receipts]] and
    * enqueue all remaining request into the [[receiptQueue]]
    */
  private[jbok] def handleReceipts(peer: Peer[F], request: GetReceipts): F[Unit] =
    peer.conn
      .request(request)
      .flatMap { res =>
        val response = res.asInstanceOf[Receipts]
        log.debug(s"downloaded ${response.receiptsForBlocks.length} Receipt(s) from ${peer.id}")
        val receivedReceipts = response.receiptsForBlocks
        val receivedHashes   = request.blockHashes.take(receivedReceipts.size)
        val remainingHashes  = request.blockHashes.drop(receivedReceipts.size)

        Stream
          .emits(receivedHashes.zip(receivedReceipts))
          .evalMap { case (hash, receipts) => history.putReceipts(hash, receipts) }
          .compile
          .drain *>
          Stream
            .emits(remainingHashes)
            .map(_.some)
            .to(receiptQueue.enqueue)
            .compile
            .drain *>
          stateRef.get
            .map(x => receivedHashes.contains(x.targetBlockHeader.hash))
            .ifM(
              ifTrue = {
                log.debug(s"finish downloading receipts")
                receiptQueue.enqueue1(None)
              },
              ifFalse = F.unit
            )
      }

  /**
    * persist received `Accounts Node`, `Storage Node` and `Code` and
    * enqueue all unfinished work into the [[nodeQueue]]
    */
  private[jbok] def handleNodeData(peer: Peer[F], request: GetNodeData): F[Unit] = {
    peer.conn
      .request(request)
      .flatMap { res =>
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
                        case hash                         => StorageRootHash(hash) :: Nil
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

                case Some(x: StorageRootHash) =>
                  val node: MptNode = RlpCodec.decode[MptNode](value.bits).require.value
                  val hashes = node match {
                    case MptNode.LeafNode(_, _) =>
                      Nil

                    case MptNode.BranchNode(branches, _) =>
                      branches.collect {
                        case Some(Left(hash)) => ContractStorageMptNodeHash(hash)
                      }

                    case MptNode.ExtensionNode(_, child) =>
                      child match {
                        case Left(hash) => ContractStorageMptNodeHash(hash) :: Nil
                        case Right(_)   => Nil
                      }
                  }
                  (receivedHashes + x,
                   childHashes ++ hashes,
                   receivedAccounts,
                   (x, value) :: receivedStorages,
                   receivedEvmCodes)

                case Some(x: ContractStorageMptNodeHash) =>
                  val node: MptNode = RlpCodec.decode[MptNode](value.bits).require.value
                  val hashes = node match {
                    case MptNode.LeafNode(_, _) =>
                      Nil

                    case MptNode.BranchNode(branches, _) =>
                      branches.collect {
                        case Some(Left(hash)) => ContractStorageMptNodeHash(hash)
                      }

                    case MptNode.ExtensionNode(_, child) =>
                      child match {
                        case Left(hash) => ContractStorageMptNodeHash(hash) :: Nil
                        case Right(_)   => Nil
                      }
                  }
                  (receivedHashes + x,
                   childHashes ++ hashes,
                   receivedAccounts,
                   (x, value) :: receivedStorages,
                   receivedEvmCodes)

                case Some(x: EvmCodeHash) =>
                  (receivedHashes + x, childHashes, receivedAccounts, receivedStorages, (x, value) :: receivedEvmCodes)
              }
          }

        val remainingHashes = request.nodeHashes.filterNot(receivedNodeHashes.contains)

        Stream
          .emits(receivedAccounts)
          .evalMap { case (hash, bytes) => history.putMptNode(hash.v, bytes) }
          .compile
          .drain *>
          Stream
            .emits(receivedStorages)
            .evalMap { case (hash, bytes) => history.putMptNode(hash.v, bytes) }
            .compile
            .drain *>
          Stream
            .emits(receivedEvmCodes)
            .evalMap { case (hash, code) => history.putCode(hash.v, code) }
            .compile
            .drain *>
          Stream
            .emits(remainingHashes ++ childrenHashes)
            .map(_.some)
            .to(nodeQueue.enqueue)
            .compile
            .drain *>
          nodeQueue.getSize
            .map(_ == 0)
            .ifM(ifTrue = {
              log.debug("finish downloading accounts")
              nodeQueue.enqueue1(None)
            }, ifFalse = F.unit)
      }
  }

  private[jbok] def isHeadersConsistent(headers: List[BlockHeader]): Boolean =
    if (headers.length > 1) {
      headers.zip(headers.tail).forall {
        case (parent, child) => parent.hash == child.parentHash && parent.number + 1 == child.number
      }
    } else {
      true
    }
}

object FastSync {
  def apply[F[_]](config: SyncConfig, pm: PeerManager[F], maxQueueSize: Int = 1024)(implicit F: ConcurrentEffect[F],
                                                                                    T: Timer[F]): F[FastSync[F]] =
    for {
      bodyQueue    <- InspectableQueue.bounded[F, Option[ByteVector]](maxQueueSize)
      state        <- Ref.of[F, FastSyncState](null)
      receiptQueue <- InspectableQueue.bounded[F, Option[ByteVector]](maxQueueSize)
      nodeQueue    <- InspectableQueue.bounded[F, Option[NodeHash]](maxQueueSize)
      jobQueue     <- PriorityQueue.bounded[F, Option[Message]](maxQueueSize)
      done         <- SignallingRef[F, Boolean](false)
    } yield new FastSync[F](config, pm, state, bodyQueue, receiptQueue, nodeQueue, jobQueue, done)
}
