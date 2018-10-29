package jbok.core.mining

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.core.ledger.{BlockResult, BloomFilter}
import jbok.core.models._
import jbok.core.sync.Synchronizer
import jbok.core.utils.ByteUtils
import jbok.crypto.authds.mpt.MPTrieStore
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

case class BlockPreparationResult[F[_]](block: Block, blockResult: BlockResult[F], stateRootHash: ByteVector)

class BlockMiner[F[_]](
    val synchronizer: Synchronizer[F],
    val stopWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = org.log4s.getLogger

  val history = synchronizer.history

  val executor = synchronizer.executor

  // sort and truncate transactions
  def prepareTransactions(stxs: List[SignedTransaction], blockGasLimit: BigInt): F[List[SignedTransaction]] = {
    val sortedByPrice = stxs
      .groupBy(stx => SignedTransaction.getSender(stx).getOrElse(Address.empty))
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

    F.pure(transactionsForBlock)
  }

  // prepare block by executing the block transactions
  def prepareBlock(header: BlockHeader, body: BlockBody): F[BlockPreparationResult[F]] = {
    val block = Block(header, body)
    for {
      (br, stxs)     <- executor.executeBlockTransactions(block, shortCircuit = false)
      worldToPersist <- executor.payReward(block, br.worldState)
      worldPersisted <- worldToPersist.persisted
    } yield {
      BlockPreparationResult(
        block.copy(body = block.body.copy(transactionList = stxs)),
        br,
        worldPersisted.stateRootHash
      )
    }
  }

  // generate a new block with specified transactions and ommers
  def generateBlock(
      parent: Block,
      stxs: List[SignedTransaction],
      ommers: List[BlockHeader]
  ): F[Block] =
    for {
      header <- executor.consensus.prepareHeader(parent, ommers)
      _ = log.info(s"prepared header: ${header}")
      txs <- prepareTransactions(stxs, header.gasLimit)
      _ = log.info(s"prepared txs: ${txs}")
      prepared <- prepareBlock(header, BlockBody(txs, ommers))
      _ = log.info(s"prepared block success")
      transactionsRoot <- calcMerkleRoot(prepared.block.body.transactionList)
      receiptsRoot     <- calcMerkleRoot(prepared.blockResult.receipts)
    } yield {
      prepared.block.copy(
        header = prepared.block.header.copy(
          transactionsRoot = transactionsRoot,
          stateRoot = prepared.stateRootHash,
          receiptsRoot = receiptsRoot,
          logsBloom =
            ByteUtils.or(BloomFilter.EmptyBloomFilter +: prepared.blockResult.receipts.map(_.logsBloomFilter): _*),
          gasUsed = prepared.blockResult.gasUsed
        )
      )
    }

  // mine a prepared block
  def mine(block: Block): F[Option[Block]] =
    executor.consensus.mine(block).attempt.map {
      case Left(e) =>
        log.error(e)(s"mining for block(${block.header.number}) failed")
        None
      case Right(b) =>
        Some(b)
    }

  def mine: F[Option[Block]] =
    for {
      parent <- executor.history.getBestBlock
      _ = log.info(s"begin mine ${parent.header.number + 1}")
      block    <- generateBlock(parent)
      minedOpt <- mine(block)
      _        <- minedOpt.fold(F.unit)(block => submitNewBlock(block))
      _ = log.info("mine end")
    } yield minedOpt

  // submit a newly mined block
  def submitNewBlock(block: Block): F[Unit] =
    synchronizer.handleMinedBlock(block)

  def miningStream: Stream[F, Block] =
    Stream
      .repeatEval(mine)
      .unNone
      .onFinalize(stopWhenTrue.set(true))

  def start: F[Unit] =
    stopWhenTrue.get.flatMap {
      case true  => stopWhenTrue.set(false) *> F.start(miningStream.interruptWhen(stopWhenTrue).compile.drain).void
      case false => F.unit
    }

  def stop: F[Unit] =
    stopWhenTrue.set(true)

  def isMining: F[Boolean] = stopWhenTrue.get.map(!_)

  //////////////////////////////
  //////////////////////////////

  def generateBlock(stxs: List[SignedTransaction], ommers: List[BlockHeader]): F[Block] =
    for {
      parent <- executor.history.getBestBlock
      block  <- generateBlock(parent, stxs, ommers)
    } yield block

  def generateBlock(parent: Block): F[Block] =
    for {
      tx   <- synchronizer.txPool.getPendingTransactions
      stxs <- synchronizer.txPool.getPendingTransactions.map(_.map(_.stx))
      _ = log.debug(s"generate block number: ${parent.header.number}, stx")
      ommers <- synchronizer.ommerPool.getOmmers(parent.header.number + 1)
      block  <- generateBlock(parent, stxs, ommers)
    } yield block

  def generateBlock(number: BigInt): F[Block] =
    for {
      parent <- executor.history.getBlockByNumber(number - 1).flatMap {
        case Some(block) => F.pure(block)
        case None        => F.raiseError[Block](new Exception(s"parent block at ${number - 1} does not exist"))
      }
      block <- generateBlock(parent)
    } yield block

  private[jbok] def calcMerkleRoot[V](entities: List[V])(implicit cv: RlpCodec[V]): F[ByteVector] =
    for {
      db   <- KeyValueDB.inMemory[F]
      mpt  <- MPTrieStore[F, Int, V](db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put(k, v) }.sequence
      root <- mpt.getRootHash
    } yield root
}

object BlockMiner {
  def apply[F[_]: ConcurrentEffect](
      synchronizer: Synchronizer[F]
  ): F[BlockMiner[F]] = SignallingRef[F, Boolean](true).map { stopWhenTrue =>
    new BlockMiner[F](
      synchronizer,
      stopWhenTrue
    )
  }
}
