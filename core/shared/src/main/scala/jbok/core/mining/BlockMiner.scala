package jbok.core.mining

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.core.NodeStatus
import jbok.core.config.MiningConfig
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock._
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.models.{SignedTransaction, _}
import jbok.core.pool.TxPool
import jbok.core.store.namespaces
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.persistent.KeyValueDB
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

  def prepare(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None
  ): F[PendingBlock] =
    for {
      header <- consensus.prepareHeader(parentOpt)
      stxs   <- stxsOpt.fold(txPool.getPendingTransactions.map(_.keys.toList))(F.pure)
      txs    <- prepareTransactions(stxs, header.gasLimit)
    } yield PendingBlock(Block(header, BlockBody(txs)))

  def execute(pending: PendingBlock): F[ExecutedBlock[F]] =
    executor.handlePendingBlock(pending)

  def mine(executed: ExecutedBlock[F]): F[Either[String, MinedBlock]] =
    consensus.mine(executed)

  def submit(mined: MinedBlock): F[Unit] =
    executor.handleMinedBlock(mined).void

  def mine1(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None
  ): F[Either[String, MinedBlock]] =
    for {
      prepared <- prepare(parentOpt, stxsOpt)
      executed <- execute(prepared)
      mined    <- mine(executed)
      _ <- mined match {
        case Left(e)  => log.i(s"mining failure: ${e}")
        case Right(b) => log.i(s"mining success") >> submit(b)
      }
    } yield mined

  def stream: Stream[F, MinedBlock] =
    Stream.eval_(log.i(s"starting Core/BlockMiner")) ++
      Stream
        .eval(status.get)
        .evalMap {
          case NodeStatus.Done => mine1()
          case _               => T.sleep(5.seconds).as("Node not ready".asLeft[MinedBlock])
        }
        .collect { case Right(x) => x }
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
      db   <- KeyValueDB.inmem[F]
      mpt  <- MerklePatriciaTrie[F](namespaces.empty, db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put[Int, V](k, v, namespaces.empty) }.sequence
      root <- mpt.getRootHash
    } yield root
}
