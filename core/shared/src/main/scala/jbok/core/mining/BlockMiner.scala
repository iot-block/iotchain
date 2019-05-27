package jbok.core.mining

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.core.NodeStatus
import jbok.core.consensus.Consensus
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.TypedBlock._
import jbok.core.models.{SignedTransaction, _}
import jbok.core.pool.TxPool
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.persistent.{ColumnFamily, MemoryKVStore}
import scodec.bits.ByteVector

import scala.concurrent.duration._

/**
  * `BlockMiner` is in charge of
  * 1. preparing a `PendingBlock` with `BlockHeader` prepared by consensus and `BlockBody` contains transactions
  * 2. call `BlockExecutor` to execute this `PendingBlock` into a `ExecutedBlock`
  * 3. call `Consensus` to seal this `ExecutedBlock` into a `MinedBlock`
  * 4. submit the `MinedBlock` to `BlockExecutor`
  */
final class BlockMiner[F[_]](
    consensus: Consensus[F],
    executor: BlockExecutor[F],
    txPool: TxPool[F],
    status: Ref[F, NodeStatus]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  private[jbok] def prepare(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None
  ): F[PendingBlock] =
    for {
      header <- consensus.prepareHeader(parentOpt)
      stxs   <- stxsOpt.fold(txPool.getPendingTransactions.map(_.keys.toList))(F.pure)
      txs    <- prepareTransactions(stxs, header.gasLimit)
    } yield PendingBlock(Block(header, BlockBody(txs)))

  private def execute(pending: PendingBlock): F[ExecutedBlock[F]] =
    executor.handlePendingBlock(pending)

  private def submit(mined: MinedBlock): F[Unit] =
    executor.handleMinedBlock(mined).void

  def mine(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None
  ): F[MinedBlock] =
    for {
      prepared <- prepare(parentOpt, stxsOpt)
      executed <- execute(prepared)
      mined    <- consensus.mine(executed)
      _        <- submit(mined)
    } yield mined

  def mineN(n: Int): F[List[MinedBlock]] =
    mine().replicateA(n)

  val stream: Stream[F, MinedBlock] =
    Stream.eval_(log.i(s"starting Core/BlockMiner")) ++
      Stream
        .eval(status.get)
        .flatMap {
          case NodeStatus.Done => Stream.eval(mine())
          case _               => Stream.sleep_(5.seconds)
        }
        .repeat

  /////////////////////////////////////
  /////////////////////////////////////

  private[jbok] def prepareTransactions(stxs: List[SignedTransaction], blockGasLimit: BigInt): F[List[SignedTransaction]] = {
    val sortedByPrice = stxs
      .groupBy(_.senderAddress.getOrElse(Address.empty))
      .values
      .toList
      .flatMap { txsFromSender =>
        val ordered = txsFromSender
          .sortBy(-_.gasPrice)
          .sortBy(_.nonce)
          .foldLeft(List.empty[SignedTransaction]) {
            case (acc, tx) =>
              if (acc.exists(_.nonce == tx.nonce)) {
                acc
              } else {
                acc :+ tx
              }
          }
          .takeWhile(_.gasLimit <= blockGasLimit)
        ordered.headOption.map(_.gasPrice -> ordered)
      }
      .sortBy { case (gasPrice, _) => -gasPrice }
      .flatMap { case (_, txs) => txs }

    val transactionsForBlock = sortedByPrice
      .scanLeft((BigInt(0), None: Option[SignedTransaction])) {
        case ((accGas, _), stx) => (accGas + stx.gasLimit, Some(stx))
      }
      .collect { case (gas, Some(stx)) => (gas, stx) }
      .takeWhile { case (gas, _) => gas <= blockGasLimit }
      .map { case (_, stx) => stx }

    log.trace(s"prepare transaction, truncated: ${transactionsForBlock.length}").as(transactionsForBlock)
  }

  private[jbok] def calcMerkleRoot[V: RlpCodec](entities: List[V]): F[ByteVector] =
    for {
      db   <- MemoryKVStore[F]
      mpt  <- MerklePatriciaTrie[F, Int, V](ColumnFamily.default, db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put(k, v) }.sequence
      root <- mpt.getRootHash
    } yield root
}
