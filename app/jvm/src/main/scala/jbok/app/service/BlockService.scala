package jbok.app.service

import cats.effect.Concurrent
import cats.implicits._
import jbok.common.math.N
import jbok.core.config.SyncConfig
import jbok.core.ledger.History
import jbok.core.models.{Block, BlockBody, BlockHeader}
import jbok.core.api.BlockAPI
import jbok.core.messages.Status
import scodec.bits.ByteVector
import spire.compat._

final class BlockService[F[_]](history: History[F], config: SyncConfig)(implicit F: Concurrent[F]) extends BlockAPI[F] {
  override def getStatus: F[Status] =
    for {
      genesis <- history.genesisHeader
      number  <- history.getBestBlockNumber
      td      <- history.getTotalDifficultyByNumber(number).map(_.getOrElse(N(0)))
    } yield Status(history.chainId, genesis.hash, number, td, "")

  override def getBestBlockNumber: F[N] =
    history.getBestBlockNumber

  override def getBlockHeaderByNumber(number: N): F[Option[BlockHeader]] =
    history.getBlockHeaderByNumber(number)

  override def getBlockHeadersByNumber(start: N, limit: Int): F[List[BlockHeader]] = {
    val headersCount = math.min(limit, config.maxBlockHeadersPerRequest)
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

  override def getBlockByNumber(number: N): F[Option[Block]] =
    history.getBlockByNumber(number)

  override def getBlockByHash(hash: ByteVector): F[Option[Block]] =
    history.getBlockByHash(hash)

  override def getTransactionCountByHash(hash: ByteVector): F[Option[Int]] =
    history.getBlockBodyByHash(hash).map(_.map(_.transactionList.length))

  override def getTotalDifficultyByNumber(number: N): F[Option[N]] =
    history.getTotalDifficultyByNumber(number)

  override def getTotalDifficultyByHash(hash: ByteVector): F[Option[N]] =
    history.getTotalDifficultyByHash(hash)
}
