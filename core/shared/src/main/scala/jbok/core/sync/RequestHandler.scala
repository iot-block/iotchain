package jbok.core.sync

import cats.effect.ConcurrentEffect
import cats.implicits._
import jbok.core.History
import jbok.core.config.Configs.SyncConfig
import jbok.core.messages._
import jbok.core.peer._
import scodec.bits.ByteVector

final case class RequestHandler[F[_]](config: SyncConfig, history: History[F])(implicit F: ConcurrentEffect[F]) {
  val service: PeerRoutes[F] = PeerRoutes.of[F] {
    case Request(peer, peerSet, GetReceipts(hashes, id)) =>
      for {
        receipts <- hashes.traverse(history.getReceiptsByHash).map(_.flatten)
      } yield peer -> Receipts(receipts, id) :: Nil

    case Request(peer, peerSet, GetBlockBodies(hashes, id)) =>
      for {
        bodies <- hashes.traverse(hash => history.getBlockBodyByHash(hash)).map(_.flatten)
      } yield peer -> BlockBodies(bodies, id) :: Nil

    case Request(peer, peerSet, GetBlockHeaders(block, maxHeaders, skip, reverse, id)) =>
      val blockNumber: F[Option[BigInt]] = block match {
        case Left(v)   => v.some.pure[F]
        case Right(bv) => history.getBlockHeaderByHash(bv).map(_.map(_.number))
      }

      blockNumber.flatMap {
        case Some(startBlockNumber) if startBlockNumber >= 0 && maxHeaders >= 0 && skip >= 0 =>
          val headersCount = math.min(maxHeaders, config.maxBlockHeadersPerRequest)

          val range = if (reverse) {
            startBlockNumber to (startBlockNumber - (skip + 1) * headersCount + 1) by -(skip + 1)
          } else {
            startBlockNumber to (startBlockNumber + (skip + 1) * headersCount - 1) by (skip + 1)
          }

          range.toList
            .traverse(history.getBlockHeaderByNumber)
            .map(_.flatten)
            .map(values => peer -> BlockHeaders(values, id) :: Nil)

        case _ =>
          F.pure(Nil)
      }

    case Request(peer, peerSet, GetNodeData(nodeHashes, id)) =>
      val nodeData = nodeHashes
        .traverse[F, Option[ByteVector]] {
          case NodeHash.StateMptNodeHash(v)           => history.getMptNode(v)
          case NodeHash.StorageRootHash(v)            => history.getMptNode(v)
          case NodeHash.ContractStorageMptNodeHash(v) => history.getMptNode(v)
          case NodeHash.EvmCodeHash(v)                => history.getCode(v)
        }
        .map(_.flatten)

      nodeData.map(values => peer -> NodeData(values, id) :: Nil)
  }
}
