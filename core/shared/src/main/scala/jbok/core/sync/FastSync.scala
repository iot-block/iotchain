package jbok.core.sync
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
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
import scodec.Codec
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

final case class FastSyncState(
    targetBlockHeader: BlockHeader,
    targetStateRootHash: StateMptNodeHash
)

/**
  * [[FastSync]] should download the [[BlockHeader]] and corresponding [[BlockBody]] and [[Receipt]]
  * plus
  *   - the world Merkle Patricia Trie under the root hash of the `pivot` height
  *   - for every [[Account]] in the trie, download its storage node and code if exists
  *
  * all remaining recent blocks, delegate to [[FullSync]]
  */
class FastSync[F[_]](
    config: SyncConfig,
    peerManager: PeerManager[F],
    bodyQueue: Queue[F, ByteVector],
    receiptQueue: Queue[F, ByteVector],
    nodeQueue: Queue[F, NodeHash],
    jobQueue: PriorityQueue[F, SyncRequest],
    done: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  import config._

  private[this] val log = org.log4s.getLogger

  private[this] val history = peerManager.history

  def start: Stream[F, Unit] =
    for {
      received <- Stream.eval(getPeersStatus)
      state    <- Stream.eval(initState(received))
      _        <- startFastSync(state)
    } yield ()

  private[jbok] def banPeer(peer: Peer[F], duration: FiniteDuration = config.banDuration): F[Unit] = ???

  private[jbok] def startFastSync(state: FastSyncState): Stream[F, Unit] = {
    log.info("start fast sync")
    for {
      bestBlockNumber <- Stream.eval(history.getBestBlockNumber)
      _ <- if (bestBlockNumber >= state.targetBlockHeader.number) {
        log.info("fast sync finished, switching to full mode")
        Stream.eval(done.set(true))
      } else {
        val getNode =
          nodeQueue.dequeue.chunkN(maxNodesPerRequest).map(chunk => GetNodeData(chunk.toList))
        val getBody =
          bodyQueue.dequeue.chunkN(maxBlockBodiesPerRequest).map(chunk => GetBlockBodies(chunk.toList))
        val getReceipt =
          receiptQueue.dequeue.chunkN(maxReceiptsPerRequest).map(chunk => GetReceipts(chunk.toList))

        val enqueue =
          (getNode.map(x => x -> 2) merge getBody.map(x => x -> 1) merge getReceipt.map(x => x -> 1)) to jobQueue.enqueue

        download(jobQueue.dequeue).concurrently(enqueue)
      }
    } yield ()
  }

  private[jbok] def getPeersStatus: F[List[(Peer[F], BlockHeader)]] = {
    def go(peers: List[Peer[F]]) =
      if (peers.length >= minPeersToChooseTargetBlock) {
        log.debug(s"asking ${peers.length} peers for block headers")
        for {
          bestNumber <- history.getBestBlockNumber
          start = bestNumber + 1
          responses <- peers.traverse(
            peer =>
              requestBlockHeaders(peer, start, maxBlockHeadersPerRequest)
                .map(headers => peer -> headers.head)
                .attemptT
                .toOption
                .value)
        } yield responses.flatten
      } else {
        log.warn(s"fast sync did not started , peers not enough ${peers.length}/${minPeersToChooseTargetBlock}")
        T.sleep(retryInterval) *> getPeersStatus
      }

    peerManager.connected >>= go
  }

  private[jbok] def getPeer: F[Peer[F]] =
    peerManager.connected.map(peers => Random.shuffle(peers).head)

  /**
    * download [[SyncResponse]] by sending [[SyncRequest]] with [[maxConcurrentRequests]]
    *
    * should block when no more concurrency or available peer
    */
  private[jbok] def download(requests: Stream[F, SyncRequest]): Stream[F, Unit] =
    requests
      .map { request =>
        for {
          peer <- Stream.eval(getPeer)
          _ <- Stream.eval(request match {
            case req: GetBlockBodies =>
              peer.conn.request[GetBlockBodies, BlockBodies](req, timeout).flatMap(res => handleBlockBodies(req, res))
            case req: GetBlockHeaders =>
              peer.conn
                .request[GetBlockHeaders, BlockHeaders](req, timeout)
                .flatMap(res => handleBlockHeaders(req, res))
            case req: GetNodeData =>
              peer.conn.request[GetNodeData, NodeData](req, timeout).flatMap(res => handleNodeData(req, res))
            case req: GetReceipts =>
              peer.conn.request[GetReceipts, Receipts](req, timeout).flatMap(res => handleReceipts(req, res))
          })
        } yield ()
      }
      .parJoin(maxConcurrentRequests)

  private[jbok] def initState(received: List[(Peer[F], BlockHeader)]): F[FastSyncState] =
    if (received.size >= minPeersToChooseTargetBlock) {
      val (chosenPeer, chosenBlockHeader) = chooseTargetBlock(received)
      val targetBlockNumber               = chosenBlockHeader.number - targetBlockOffset
      log.info(s"fetching block headers of target ${targetBlockNumber}")
      val targetHeaders = received.traverse {
        case (peer, _) =>
          requestBlockHeaders(peer, targetBlockNumber, 1).map { headers =>
            headers.find(_.number == targetBlockNumber) match {
              case Some(targetBlockHeader) =>
                log.info(s"pre start fast sync, got one target block ${targetBlockHeader} from ${peer.id}")
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
          log.info(s"got enough block headers that have the same stateRoot, start fast sync to ${targetBlockHeader}")
          FastSyncState(targetBlockHeader, StateMptNodeHash(targetBlockHeader.stateRoot)).pure[F]
        } else {
          log.info(s"""could not get enough block headers that have the same stateRoot
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
    log.debug(s"request block headers from ${peer.id}")
    val request = GetBlockHeaders(Left(start), limit, 0, false)
    for {
      response <- peer.conn.request[Message, BlockHeaders](request, timeout)
    } yield response.headers
  }

  /**
    * persist received [[BlockHeaders]] and
    * start corresponding [[GetBlockBodies]] and [[GetReceipts]] request
    */
  private[jbok] def handleBlockHeaders(request: GetBlockHeaders, response: BlockHeaders): F[Unit] = {
    val blockHashes = response.headers.map(_.hash)

    if (isHeadersConsistent(response.headers)) {
      Stream
        .emits(response.headers)
        .evalMap(history.putBlockHeader)
        .compile
        .drain *>
        Stream
          .emits(blockHashes)
          .to(bodyQueue.enqueue)
          .compile
          .drain *>
        Stream
          .emits(blockHashes)
          .to(receiptQueue.enqueue)
          .compile
          .drain
    } else {
      // TODO we should consider ban peer here
      F.unit
    }
  }

  /**
    * persist received [[BlockBodies]] and
    * enqueue all remaining request into the [[bodyQueue]]
    */
  private[jbok] def handleBlockBodies(request: GetBlockBodies, response: BlockBodies): F[Unit] = {
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
        .to(bodyQueue.enqueue)
        .compile
        .drain
  }

  /**
    * persist received [[Receipts]] and
    * enqueue all remaining request into the [[receiptQueue]]
    */
  private[jbok] def handleReceipts(request: GetReceipts, response: Receipts): F[Unit] = {
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
        .to(receiptQueue.enqueue)
        .compile
        .drain
  }

  /**
    * persist received `Accounts Node`, `Storage Node` and `Code` and
    * enqueue all unfinished work into the [[nodeQueue]]
    */
  private[jbok] def handleNodeData(request: GetNodeData, response: NodeData): F[Unit] = {
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
      .evalMap { case (hash, bytes) => history.putAccountNode(hash.v, bytes) }
      .compile
      .drain *>
      Stream
        .emits(receivedStorages)
        .evalMap { case (hash, bytes) => history.putStorageNode(hash.v, bytes) }
        .compile
        .drain *>
      Stream
        .emits(receivedEvmCodes)
        .evalMap { case (hash, code) => history.putCode(hash.v, code) }
        .compile
        .drain *>
      Stream
        .emits(remainingHashes ++ childrenHashes)
        .to(nodeQueue.enqueue)
        .compile
        .drain
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
      bodyQueue    <- Queue.bounded[F, ByteVector](maxQueueSize)
      receiptQueue <- Queue.bounded[F, ByteVector](maxQueueSize)
      nodeQueue    <- Queue.bounded[F, NodeHash](maxQueueSize)
      jobQueue     <- PriorityQueue.bounded[F, SyncRequest](maxQueueSize)
      done         <- SignallingRef[F, Boolean](false)
    } yield new FastSync[F](config, pm, bodyQueue, receiptQueue, nodeQueue, jobQueue, done)
}
