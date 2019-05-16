package jbok.app.service

import cats.effect.Concurrent
import cats.implicits._
import jbok.core.config.SyncConfig
import jbok.core.ledger.History
import jbok.core.models.{Block, BlockBody, BlockHeader}
import jbok.core.api.BlockAPI
import scodec.bits.ByteVector

final class BlockService[F[_]](history: History[F], helper: ServiceHelper[F], config: SyncConfig)(implicit F: Concurrent[F]) extends BlockAPI[F] {
  override def getBestBlockNumber: F[BigInt] =
    history.getBestBlockNumber

  override def getBlockHeaderByNumber(number: BigInt): F[Option[BlockHeader]] =
    history.getBlockHeaderByNumber(number)

  override def getBlockHeadersByNumber(start: BigInt, limit: Int): F[List[BlockHeader]] = {
    val headersCount = limit min config.maxBlockHeadersPerRequest
    val range        = List.range(start, start + headersCount)
    range.traverse(history.getBlockHeaderByNumber).map(_.flatten)
  }

  override def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]] =
    history.getBlockHeaderByHash(hash)

  override def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]] =
    history.getBlockBodyByHash(hash)

  override def getBlockBodies(hashes: List[ByteVector]): F[List[BlockBody]] =
    hashes
      .take(config.maxBlockBodiesPerRequest)
      .traverse(hash => history.getBlockBodyByHash(hash))
      .map(_.flatten)

  override def getBlockByNumber(number: BigInt): F[Option[Block]] =
    history.getBlockByNumber(number)

  override def getBlockByHash(hash: ByteVector): F[Option[Block]] =
    history.getBlockByHash(hash)

  override def getTransactionCountByHash(hash: ByteVector): F[Option[Int]] =
    history.getBlockBodyByHash(hash).map(_.map(_.transactionList.length))

  override def getTotalDifficultyByNumber(number: BigInt): F[Option[BigInt]] =
    history.getTotalDifficultyByNumber(number)

  override def getTotalDifficultyByHash(hash: ByteVector): F[Option[BigInt]] =
    history.getTotalDifficultyByHash(hash)
}
