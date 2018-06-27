package jbok.core.store

import cats.data.OptionT
import cats.effect.Sync
import jbok.core.models.{BlockBody, BlockHeader, Receipt}
import scodec.bits.ByteVector
import cats.implicits._

class BlockHeaderStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, BlockHeader](Namespaces.BlockHeader, db)

class BlockBodyStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, BlockBody](Namespaces.BlockBody, db)

class ReceiptStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, List[Receipt]](Namespaces.Receipts, db)

class BlockNumberHashStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, BigInt, ByteVector](Namespaces.Heights, db)

case class TransactionLocation(blockHash: ByteVector, txIndex: Int)

class TransactionLocationStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, TransactionLocation](Namespaces.TransactionLocation, db)

class AppStateStore[F[_]: Sync](db: KeyValueDB[F]) extends KeyValueStore[F, String, ByteVector](Namespaces.AppStateNamespace, db) {
  private val BestBlockNumber = "BestBlockNumber"
  private val FastSyncDone = "FastSyncDone"
  private val EstimatedHighestBlock = "EstimatedHighestBlock"
  private val SyncStartingBlock = "SyncStartingBlock"

  def getBestBlockNumber: F[BigInt] = get(BestBlockNumber).flatMap(v => decode[BigInt](v))

  def putBestBlockNumber(bestBlockNumber: BigInt): F[Unit] = encode(bestBlockNumber).flatMap(bn => put(BestBlockNumber, bn))

  def getFastSyncDone: F[Boolean] = get(FastSyncDone).flatMap(v => decode[Boolean](v))

  def putFastSyncDone(b: Boolean = true): F[Unit] = encode(b).flatMap(v => put(FastSyncDone, v))

  def getEstimatedHighestBlock: F[BigInt] = {
    val h= for {
      v <- OptionT(getOpt(EstimatedHighestBlock))
      h <- OptionT.liftF(decode[BigInt](v))
    } yield h

    h.value.map(_.getOrElse(0))
  }

  def putEstimatedHighestBlock(n: BigInt): F[Unit] = {
    encode(n).flatMap(v => put(EstimatedHighestBlock, v))
  }

  def getSyncStartingBlock: F[BigInt] = {
    val h= for {
      v <- OptionT(getOpt(SyncStartingBlock))
      h <- OptionT.liftF(decode[BigInt](v))
    } yield h

    h.value.map(_.getOrElse(0))
  }

  def putSyncStartingBlock(n: BigInt): F[Unit] = {
    encode(n).flatMap(v => put(SyncStartingBlock, v))
  }
}
