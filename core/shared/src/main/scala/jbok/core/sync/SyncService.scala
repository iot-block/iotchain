package jbok.core.sync

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import jbok.core.History
import jbok.core.config.Configs.SyncConfig
import jbok.core.messages._
import scodec.bits.ByteVector

case class SyncService[F[_]](config: SyncConfig, history: History[F])(implicit F: ConcurrentEffect[F]) {
  private[this] val log = org.log4s.getLogger

  def handle(request: SyncRequest): F[Option[SyncResponse]] = request match {
    case GetReceipts(hashes, id) =>
      for {
        receipts <- hashes.traverse(history.getReceiptsByHash).map(_.flatten)
      } yield Receipts(receipts, id).some

    case GetBlockBodies(hashes, id) =>
      for {
        bodies <- hashes.traverse(hash => history.getBlockBodyByHash(hash)).map(_.flatten)
      } yield BlockBodies(bodies, id).some

    case GetBlockHeaders(block, maxHeaders, skip, reverse, id) =>
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
            .map(values => BlockHeaders(values, id).some)

        case _ =>
          log.warn(s"got request for block headers with invalid block hash/number")
          F.pure(None)
      }

    case GetNodeData(nodeHashes, id) =>
      val nodeData = nodeHashes
        .traverse[F, Option[ByteVector]] {
          case NodeHash.StateMptNodeHash(v)           => history.getAccountNode(v)
          case NodeHash.StorageRootHash(v)            => history.getStorageNode(v)
          case NodeHash.ContractStorageMptNodeHash(v) => history.getStorageNode(v)
          case NodeHash.EvmCodeHash(v)                => history.getCode(v)
        }
        .map(_.flatten)

      nodeData.map(values => Some(NodeData(values, id)))
  }

  val pipe: Pipe[F, Message, Message] = _.evalMap[F, Option[Message]] {
    case request: SyncRequest => handle(request).map(_.map(_.asInstanceOf[Message]))
    case _                    => F.pure(None)
  }.unNone
}
