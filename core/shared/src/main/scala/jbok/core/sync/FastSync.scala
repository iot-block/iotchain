package jbok.core.sync

import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.{InspectableQueue, Queue}
import jbok.codec.rlp.implicits._
import jbok.common._
import jbok.core.config.Configs.SyncConfig
import jbok.core.messages._
import jbok.core.models._
import jbok.core.peer.{Peer, PeerManager}
import jbok.core.sync.NodeHash.{EvmCodeHash, StateMptNodeHash, StorageMptNodeHash}
import jbok.crypto._
import jbok.crypto.authds.mpt.MptNode
import jbok.network.{Message, Request}
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
    peerManager: PeerManager[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = jbok.common.log.getLogger("FastSync")

  import FastSync._
  import config._

  private[this] val history = peerManager.history

  val stream: Stream[F, Unit] = {
    Stream.eval(F.delay(log.info(s"start fast sync"))) ++ Stream
      .eval(initState)
      .flatMap {
        case Some(state) => fastSync(state)
        case None        => Stream.empty
      }
      .onFinalize(F.delay(s"finish fast sync"))
  }

  private def initState: F[Option[FastSyncState[F]]] =
    for {
      peersStatus <- getPeersStatus
      bestNumber  <- history.getBestBlockNumber
      bestPeerNumber = peersStatus.map(_._2.bestNumber).foldLeft(BigInt(0))(_ max _)
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

  private def fastSync(state: FastSyncState[F]): Stream[F, Unit] =
    downloadAll(state) ++ downloadNodeData(state)

  def downloadHeaders(peer: Peer[F], current: BigInt, target: BigInt): F[List[BlockHeader]] = {
    val start = current + 1
    val limit = (BigInt(maxBlockHeadersPerRequest) min (target - start + 1)).toInt
    for {
      request               <- Request.binary[F, GetBlockHeaders]("GetBlockHeaders", GetBlockHeaders(Left(start), limit, 0, false))
      BlockHeaders(headers) <- peer.conn.expect[BlockHeaders](request)
    } yield headers
  }

  def downloadBlocks(peer: Peer[F], current: BigInt, target: BigInt): F[Unit] =
    for {
      headers <- downloadHeaders(peer, current, target)
      hashes = headers.map(_.hash)
      bodyReq             <- Request.binary[F, GetBlockBodies]("GetBlockBodies", GetBlockBodies(hashes))
      receiptReq          <- Request.binary[F, GetReceipts]("GetReceipts", GetReceipts(hashes))
      BlockBodies(bodies) <- peer.conn.expect[BlockBodies](bodyReq)
      Receipts(receipts)  <- peer.conn.expect[Receipts](receiptReq)
      _                   <- headers.traverse(header => history.putBlockHeader(header, updateTD = true))
      _                   <- hashes.zip(bodies).traverse { case (hash, body) => history.putBlockBody(hash, body) }
      _                   <- hashes.zip(receipts).traverse { case (hash, receipts) => history.putReceipts(hash, receipts) }
      _                   <- history.putBestBlockNumber(headers.map(_.number).foldLeft(BigInt(0))(_ max _))
    } yield ()

  def downloadAll(state: FastSyncState[F]): Stream[F, Unit] =
    Stream
      .repeatEval[F, Option[Unit]] {
        for {
          bestBlockNumber <- history.getBestBlockNumber
          res <- if (bestBlockNumber >= state.target.number) {
            F.delay(log.info("fast sync finished, switching to full mode")).as(none)
          } else {
            for {
              peer <- randomPeer(state.goodPeers)
              _    <- downloadBlocks(peer, bestBlockNumber, state.target.number)
            } yield ().some
          }
        } yield res
      }
      .takeWhile(_.isEmpty)
      .unNoneTerminate

  def downloadNodeData(state: FastSyncState[F]): Stream[F, Unit] =
    Stream
      .eval(Queue.unbounded[F, GetNodeData])
      .flatMap(queue =>
        queue.dequeue.evalMap { getNodeData =>
          for {
            peer    <- randomPeer(state.goodPeers)
            request <- Request.binary[F, GetNodeData]("GetNodeData", getNodeData)
            resp    <- peer.conn.expect[NodeData](request)
            requested = getNodeData.nodeHashes.map(x => x.v -> x).toMap
            hashes <- resp.values.traverse(value => handleNode(requested(value.kec256), value)).map(_.flatten)
            _      <- queue.enqueue1(GetNodeData(hashes))
          } yield ()
      })

  def handleNode(nodeHash: NodeHash, value: ByteVector): F[List[NodeHash]] = nodeHash match {
    case StateMptNodeHash(hash) =>
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

      history.putMptNode(hash, value).as(hashes)

    case StorageMptNodeHash(hash) =>
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
      history.putMptNode(hash, value).as(hashes)

    case EvmCodeHash(hash) =>
      history.putCode(hash, value).as(Nil)
  }

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
          downloadHeaders(peer, status.bestNumber, 1)
            .map(headers => peer -> headers.head)
            .attemptT
            .toOption
            .value
      }
      .map(_.flatten)
  }

  private def randomPeer(peers: List[Peer[F]]): F[Peer[F]] =
    peerManager.connected.flatMap { connected =>
      val available = connected.filter(p => peers.contains(p))
      Random.shuffle(available) match {
        case head :: _ => F.pure(head)
        case Nil       => F.raiseError(new Exception(s"no available peers"))
      }
    }

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
          downloadHeaders(peer, targetBlockNumber, 1).map { headers =>
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

//  private[jbok] def handleNodeData(peer: Peer[F], request: GetNodeData): F[Unit] = {
//    Request[F, GetNodeData]("getNodeData", request).flatMap { request =>
//      peer.conn
//        .expect[NodeData](request)
//        .timeout(config.requestTimeout)
//        .attempt
//        .flatMap {
//          case Right(response) =>
//            log.debug(s"downloaded ${response.values.length} NodeData from ${peer.id}")
//            val requested = request.nodeHashes.map(x => x.v -> x).toMap
//            val (receivedNodeHashes, childrenHashes, receivedAccounts, receivedStorages, receivedEvmCodes) =
//              response.values.foldLeft(
//                (Set[NodeHash](),
//                 List[NodeHash](),
//                 List[(NodeHash, ByteVector)](),
//                 List[(NodeHash, ByteVector)](),
//                 List[(NodeHash, ByteVector)]())) {
//                case ((receivedHashes, childHashes, receivedAccounts, receivedStorages, receivedEvmCodes), value) =>
//                  val receivedHash = value.kec256
//                  requested.get(receivedHash) match {
//                    case None =>
//                      (receivedHashes, childHashes, receivedAccounts, receivedStorages, receivedEvmCodes)
//
//                    case Some(x: StateMptNodeHash) =>
//                      val node: MptNode = RlpCodec.decode[MptNode](value.bits).require.value
//                      val hashes = node match {
//                        case MptNode.LeafNode(_, value) =>
//                          val account = RlpCodec.decode[Account](value.bits).require.value
//                          val codeHash = account.codeHash match {
//                            case Account.EmptyCodeHash => Nil
//                            case hash                  => EvmCodeHash(hash) :: Nil
//                          }
//                          val storageHash = account.storageRoot match {
//                            case Account.EmptyStorageRootHash => Nil
//                            case hash                         => StorageMptNodeHash(hash) :: Nil
//                          }
//                          codeHash ++ storageHash
//
//                        case MptNode.BranchNode(branches, _) =>
//                          branches.collect {
//                            case Some(Left(hash)) => StateMptNodeHash(hash)
//                          }
//
//                        case MptNode.ExtensionNode(_, child) =>
//                          child match {
//                            case Left(hash) => StateMptNodeHash(hash) :: Nil
//                            case Right(_)   => Nil
//                          }
//                      }
//                      (receivedHashes + x,
//                       childHashes ++ hashes,
//                       (x, value) :: receivedAccounts,
//                       receivedStorages,
//                       receivedEvmCodes)
//
//                    case Some(x: StorageMptNodeHash) =>
//                      val node: MptNode = RlpCodec.decode[MptNode](value.bits).require.value
//                      val hashes = node match {
//                        case MptNode.LeafNode(_, _) =>
//                          Nil
//
//                        case MptNode.BranchNode(branches, _) =>
//                          branches.collect {
//                            case Some(Left(hash)) => StorageMptNodeHash(hash)
//                          }
//
//                        case MptNode.ExtensionNode(_, child) =>
//                          child match {
//                            case Left(hash) => StorageMptNodeHash(hash) :: Nil
//                            case Right(_)   => Nil
//                          }
//                      }
//                      (receivedHashes + x,
//                       childHashes ++ hashes,
//                       receivedAccounts,
//                       (x, value) :: receivedStorages,
//                       receivedEvmCodes)
//
//                    case Some(x: EvmCodeHash) =>
//                      (receivedHashes + x,
//                       childHashes,
//                       receivedAccounts,
//                       receivedStorages,
//                       (x, value) :: receivedEvmCodes)
//                  }
//              }
//
//            val remainingHashes = request.nodeHashes.filterNot(receivedNodeHashes.contains)
//
//            (Stream
//              .emits(receivedAccounts)
//              .covary[F]
//              .evalMap { case (hash, bytes) => history.putMptNode(hash.v, bytes) } ++
//              Stream
//                .emits(receivedStorages)
//                .covary[F]
//                .evalMap { case (hash, bytes) => history.putMptNode(hash.v, bytes) } ++
//              Stream
//                .emits(receivedEvmCodes)
//                .covary[F]
//                .evalMap { case (hash, code) => history.putCode(hash.v, code) } ++
//              Stream
//                .emits(remainingHashes ++ childrenHashes)
//                .covary[F]
//                .to(nodeQueue.enqueue)).compile.drain
//
//          case _ =>
//            F.unit
////            jobQueue.enqueue1(Some(request))
//        }
//    }
//  }

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
  final case class FastSyncState[F[_]](target: BlockHeader, goodPeers: List[Peer[F]])

  def apply[F[_]](config: SyncConfig, pm: PeerManager[F], maxQueueSize: Int = 1024)(implicit F: ConcurrentEffect[F],
                                                                                    T: Timer[F]): F[FastSync[F]] =
    for {
      bodyQueue    <- InspectableQueue.bounded[F, ByteVector](maxQueueSize)
      receiptQueue <- InspectableQueue.bounded[F, ByteVector](maxQueueSize)
      nodeQueue    <- InspectableQueue.bounded[F, NodeHash](maxQueueSize)
      jobQueue     <- InspectableQueue.bounded[F, Option[Request[F]]](maxQueueSize)
      running      <- Ref.of[F, Int](0)
    } yield new FastSync[F](config, pm)
}
