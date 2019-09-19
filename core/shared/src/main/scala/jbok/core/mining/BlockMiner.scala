package jbok.core.mining

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.codec.rlp.RlpCodec
import jbok.common.log.Logger
import jbok.common.math.N
import jbok.core.NodeStatus
import jbok.core.config.MiningConfig
import jbok.core.consensus.Consensus
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.ledger.TypedBlock._
import jbok.core.models.{SignedTransaction, _}
import jbok.core.pool.TxPool
import jbok.persistent.mpt.MerklePatriciaTrie
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
    config: MiningConfig,
    consensus: Consensus[F],
    executor: BlockExecutor[F],
    txPool: TxPool[F],
    status: Ref[F, NodeStatus],
    history: History[F]
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
      _        <- log.i(s"${prepared.block.tag} is prepared")
      executed <- execute(prepared)
      _        <- log.i(s"${executed.block.tag} is executed")
      mined    <- consensus.mine(executed)
      _        <- log.i(s"${mined.block.tag} is mined")
      _        <- submit(mined)
      _        <- log.i(s"${mined.block.tag} is submitted")
    } yield mined

  def mineN(n: Int): F[List[MinedBlock]] =
    mine().replicateA(n)

  val stream: Stream[F, Unit] =
    if (config.enabled) {
      Stream.eval_(log.i(s"starting Core/BlockMiner")) ++
        (
          Stream.eval_(log.i(s"minerStream start")) ++
          Stream
            .eval(status.get)
            .flatMap {
              case NodeStatus.Done => Stream.eval_(log.i(s"mine")) ++ Stream.eval_(mine())
              case s               => Stream.eval_(log.i(s"miner sleep, status = $s")) ++ Stream.sleep_(5.seconds)
            }
            .handleErrorWith(e => Stream.eval(log.e("miner has an failure", e))) ++
          Stream.eval_(log.i(s"minerStream end"))
        ).repeat
    } else {
      Stream.empty
    }

  /////////////////////////////////////
  /////////////////////////////////////

  private[jbok] def filterNonceInvalid(stxs: List[SignedTransaction]): F[List[SignedTransaction]] =
    for {
      parentStateRoot <- history.getBestBlockHeader.map(_.stateRoot)
      world <- history.getWorldState(
        UInt256.zero,
        Some(parentStateRoot),
        false
      )
      groupTxs = stxs
        .sortBy(_.nonce)
        .groupBy(_.senderAddress.getOrElse(Address.empty))
      accountNonces = groupTxs.map{
          case (senderAddress, _) => (senderAddress,world.getAccountOpt(senderAddress).getOrElse(Account.empty(UInt256.zero)).map(_.nonce))
        }
      refPoolNonces <- Ref.of(groupTxs.map{
        case (senderAddress, txs) => (senderAddress,txs.map(_.nonce))
      })
      filteredStxs <- stxs.filterA(tx => for {
        senderAddress <- F.pure(tx.senderAddress.getOrElse(Address.empty))
        senderNonce <- accountNonces.get(senderAddress) match {
          case Some(nonce) => nonce
          case None => F.pure(UInt256.zero)
        }
        senderNonceMatch = senderNonce==tx.nonce
        (poolContainPreNonce,filteredNonces) <- refPoolNonces.get.map(_.get(senderAddress) match {
          case Some(nonces) => {
            val contain = nonces.contains(tx.nonce-1)
            if (!contain && !senderNonceMatch){
              val filteredNonces = nonces.filter(n => n<tx.nonce)
              (contain,filteredNonces)
            }else{
              (contain,nonces)
            }
          }
          case None => (false,List.empty)
        })
        _ <- if (!poolContainPreNonce && !senderNonceMatch){
          refPoolNonces.update(_ + (senderAddress -> filteredNonces))
        }else F.unit
        nonceMatch = senderNonceMatch || poolContainPreNonce
      }yield nonceMatch)

    }yield filteredStxs

  private[jbok] def prepareTransactions(stxs: List[SignedTransaction], blockGasLimit: N): F[List[SignedTransaction]] = {
    for {
      _ <- log.d(s"prepareTransactions")
      sortedByPrice = stxs
        .groupBy(_.senderAddress.getOrElse(Address.empty))
        .values
        .toList
        .flatMap { txsFromSender =>
          val txNonces = txsFromSender.map(_.nonce)
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

      _ <- log.d(s"sortedByPrice: ${sortedByPrice}")
      filteredStxs <- filterNonceInvalid(sortedByPrice)
      _ <- log.d(s"filteredStxs: ${filteredStxs}")

      transactionsForBlock = filteredStxs
        .scanLeft((N(0), None: Option[SignedTransaction])) {
          case ((accGas, _), stx) => (accGas + stx.gasLimit, Some(stx))
        }
        .collect { case (gas, Some(stx)) => (gas, stx) }
        .takeWhile { case (gas, _) => gas <= blockGasLimit }
        .map { case (_, stx) => stx }
    }yield transactionsForBlock
  }

  private[jbok] def calcMerkleRoot[V: RlpCodec](entities: List[V]): F[ByteVector] =
    for {
      db   <- MemoryKVStore[F]
      mpt  <- MerklePatriciaTrie[F, Int, V](ColumnFamily.default, db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put(k, v) }.sequence
      root <- mpt.getRootHash
    } yield root
}
