package jbok.core.mining

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.MiningConfig
import jbok.core.consensus.Consensus
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.TypedBlock._
import jbok.core.models.{SignedTransaction, _}
import jbok.core.store.namespaces
import jbok.core.sync.SyncManager
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.persistent.KeyValueDB
import scodec.Codec
import scodec.bits.ByteVector

/**
  * [[BlockMiner]] is in charge of
  * 1. preparing a [[PendingBlock]] with [[BlockHeader]] prepared by consensus and [[BlockBody]] contains transactions
  * 2. call [[BlockExecutor]] to execute this [[PendingBlock]] into a [[ExecutedBlock]]
  * 3. call [[Consensus]] to seal this [[ExecutedBlock]] into a [[MinedBlock]]
  * 4. submit the [[MinedBlock]] to [[BlockExecutor]]
  */
case class BlockMiner[F[_]](
    config: MiningConfig,
    syncManager: SyncManager[F],
    haltWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("BlockMiner")

  val executor = syncManager.executor

  val history = executor.history

  val txPool = executor.txPool

  def prepare(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None,
      ommersOpt: Option[List[BlockHeader]] = None
  ): F[PendingBlock] =
    for {
      header <- executor.consensus.prepareHeader(parentOpt)
      stxs   <- stxsOpt.fold(txPool.getPendingTransactions.map(_.map(_.stx)))(F.pure)
      txs    <- prepareTransactions(stxs, header.gasLimit)
    } yield PendingBlock(Block(header, BlockBody(txs, Nil)))

  def execute(pending: PendingBlock): F[ExecutedBlock[F]] =
    executor.handlePendingBlock(pending)

  def mine(executed: ExecutedBlock[F]): F[MinedBlock] =
    executor.consensus.mine(executed)

  def submit(mined: MinedBlock): F[Unit] =
    for {
      blocks <- executor.handleMinedBlock(mined)
      messages = blocks.flatMap(syncManager.broadcastBlock)
      _ <- syncManager.sendMessages(messages)
    } yield ()

  def mine1(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None,
      ommersOpt: Option[List[BlockHeader]] = None
  ): F[MinedBlock] =
    for {
      prepared <- prepare(parentOpt, stxsOpt, ommersOpt)
      executed <- execute(prepared)
      mined    <- mine(executed)
      _        <- submit(mined)
    } yield mined

  def stream: Stream[F, MinedBlock] =
    Stream
      .repeatEval(mine1())
      .onFinalize(haltWhenTrue.set(true))

  /////////////////////////////////////
  /////////////////////////////////////

  private[jbok] def prepareTransactions(stxs: List[SignedTransaction],
                                        blockGasLimit: BigInt): F[List[SignedTransaction]] = {
    log.trace(s"prepare transaction, available: ${stxs.length}")
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
      .scanLeft(BigInt(0), None: Option[SignedTransaction]) {
        case ((accGas, _), stx) => (accGas + stx.gasLimit, Some(stx))
      }
      .collect { case (gas, Some(stx)) => (gas, stx) }
      .takeWhile { case (gas, _) => gas <= blockGasLimit }
      .map { case (_, stx) => stx }

    log.trace(s"prepare transaction, truncated: ${transactionsForBlock.length}")
    F.pure(transactionsForBlock)
  }

  private[jbok] def calcMerkleRoot[V: Codec](entities: List[V]): F[ByteVector] =
    for {
      db   <- KeyValueDB.inmem[F]
      mpt  <- MerklePatriciaTrie[F](namespaces.empty, db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put[Int, V](k, v, namespaces.empty) }.sequence
      root <- mpt.getRootHash
    } yield root
}

object BlockMiner {
  def apply[F[_]](config: MiningConfig, syncManager: SyncManager[F])(implicit F: ConcurrentEffect[F],
                                                                     T: Timer[F]): F[BlockMiner[F]] =
    SignallingRef[F, Boolean](true).map(halt => BlockMiner(config, syncManager, halt))
}
