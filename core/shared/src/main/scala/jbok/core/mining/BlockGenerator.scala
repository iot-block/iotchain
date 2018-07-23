package jbok.core.mining

import java.time.Instant
import java.util.function.UnaryOperator

import cats.data.EitherT
import cats.implicits._
import cats.effect.Sync
import fs2.async.Ref
import jbok.core.BlockChain
import jbok.core.configs.{BlockChainConfig, MiningConfig}
import jbok.core.ledger.{BlockResult, BloomFilter, Ledger}
import jbok.core.models._
import jbok.core.utils.ByteUtils
import jbok.core.validators.Validators
import jbok.crypto.authds.mpt.{MPTrie, MPTrieStore}
import scodec.Codec
import scodec.bits.ByteVector

trait BlockPreparationError
object BlockPreparationError {
  case class TxError(reason: String) extends BlockPreparationError
}

case class PendingBlock(block: Block, receipts: Seq[Receipt])
case class BlockPreparationResult[F[_]](block: Block, blockResult: BlockResult[F], stateRootHash: ByteVector)
//
//class BlockGenerator[F[_]](
//    blockchain: BlockChain[F],
//    blockchainConfig: BlockChainConfig,
//    miningConfig: MiningConfig,
//    ledger: Ledger[F],
//    validators: Validators[F],
//    blockTimestampProvider: BlockTimestampProvider = DefaultBlockTimestampProvider,
//    cache: Ref[F, List[PendingBlock]]
//)(implicit F: Sync[F]) {
//
////  val difficulty = new DifficultyCalculator(blockchainConfig)
//
////  private val cache: AtomicReference[List[PendingBlock]] = new AtomicReference(Nil)
//
////  def generateBlockForMining(
////      parent: Block,
////      transactions: List[SignedTransaction],
////      ommers: List[BlockHeader],
////      beneficiary: Address
////  ): EitherT[F, BlockPreparationError, PendingBlock] = {
////    val blockNumber = parent.header.number + 1
////    val parentHash = parent.header.hash
////
////    validators.ommersValidator.validate(parentHash, blockNumber, ommers, blockchain)
////      .semiflatMap(_ => {
////
////      })
////
////    val result =
////      validators.ommersValidator.validate(parentHash, blockNumber, ommers, blockchain).left.map(InvalidOmmers).flatMap {
////        _ =>
////          val blockTimestamp = blockTimestampProvider.getEpochSecond
////          val header: BlockHeader = prepareHeader(blockNumber, ommers, beneficiary, parent, blockTimestamp)
////          val transactionsForBlock: List[SignedTransaction] = prepareTransactions(transactions, header.gasLimit)
////          val body = BlockBody(transactionsForBlock, ommers)
////          val block = Block(header, body)
////
////          val prepared = ledger.prepareBlock(block) match {
////            case BlockPreparationResult(prepareBlock, BlockResult(_, gasUsed, receipts), stateRoot) =>
////              val receiptsLogs: Seq[Array[Byte]] = BloomFilter.EmptyBloomFilter.toArray +: receipts.map(
////                _.logsBloomFilter.toArray)
////              val bloomFilter = ByteVector(ByteUtils.or(receiptsLogs: _*))
////
////              PendingBlock(
////                block.copy(
////                  header = block.header.copy(
////                    transactionsRoot =
////                      buildMpt(prepareBlock.body.transactionList, SignedTransaction.byteArraySerializable),
////                    stateRoot = stateRoot,
////                    receiptsRoot = buildMpt(receipts, Receipt.byteArraySerializable),
////                    logsBloom = bloomFilter,
////                    gasUsed = gasUsed
////                  ),
////                  body = prepareBlock.body
////                ),
////                receipts
////              )
////          }
////          Right(prepared)
////      }
////
////    result.right.foreach(b =>
////      cache.updateAndGet(new UnaryOperator[List[PendingBlock]] {
////        override def apply(t: List[PendingBlock]): List[PendingBlock] =
////          (b :: t).take(miningConfig.blockCacheSize)
////      }))
////
////    result
////  }
//
////  private def prepareTransactions(transactions: Seq[SignedTransaction], blockGasLimit: BigInt) =
////    val sortedTransactions = transactions
////      .groupBy(_.senderAddress)
////      .values
////      .toList
////      .flatMap { txsFromSender =>
////        val ordered = txsFromSender
////          .sortBy(-_.tx.gasPrice)
////          .sortBy(_.tx.nonce)
////          .foldLeft(Seq.empty[SignedTransaction]) {
////            case (txs, tx) =>
////              if (txs.exists(_.tx.nonce == tx.tx.nonce)) {
////                txs
////              } else {
////                txs :+ tx
////              }
////          }
////          .takeWhile(_.tx.gasLimit <= blockGasLimit)
////        ordered.headOption.map(_.tx.gasPrice -> ordered)
////      }
////      .sortBy { case (gasPrice, _) => gasPrice }
////      .reverse
////      .flatMap { case (_, txs) => txs }
////
////    val transactionsForBlock = sortedTransactions
////      .scanLeft(BigInt(0), None: Option[SignedTransaction]) {
////        case ((accumulatedGas, _), stx) => (accumulatedGas + stx.tx.gasLimit, Some(stx))
////      }
////      .collect { case (gas, Some(stx)) => (gas, stx) }
////      .takeWhile { case (gas, _) => gas <= blockGasLimit }
////      .map { case (_, stx) => stx }
////    transactionsForBlock
////  }
//
//  private def prepareHeader(
//      blockNumber: BigInt,
//      ommers: Seq[BlockHeader],
//      beneficiary: Address,
//      parent: Block,
//      blockTimestamp: Long
//  ) = {
//
//    BlockHeader(
//      parentHash = parent.header.hash,
//      ommersHash = ByteVector(kec256(ommers.toBytes: Array[Byte])),
//      beneficiary = beneficiary.bytes,
//      stateRoot = ByteVector.empty,
//      //we are not able to calculate transactionsRoot here because we do not know if they will fail
//      transactionsRoot = ByteVector.empty,
//      receiptsRoot = ByteVector.empty,
//      logsBloom = ByteVector.empty,
//      difficulty = difficulty.calculateDifficulty(blockNumber, blockTimestamp, parent.header),
//      number = blockNumber,
//      gasLimit = calculateGasLimit(parent.header.gasLimit),
//      gasUsed = 0,
//      unixTimestamp = blockTimestamp,
//      extraData = daoForkConfig
//        .flatMap(daoForkConfig => daoForkConfig.getExtraData(blockNumber))
//        .getOrElse(miningConfig.headerExtraData),
//      mixHash = ByteVector.empty,
//      nonce = ByteVector.empty
//    )
//  }
//
//  def getPrepared(powHeaderHash: ByteVector): Option[PendingBlock] =
//    cache
//      .getAndUpdate(new UnaryOperator[List[PendingBlock]] {
//        override def apply(t: List[PendingBlock]): List[PendingBlock] =
//          t.filterNot(pb => ByteVector(kec256(BlockHeader.getEncodedWithoutNonce(pb.block.header))) == powHeaderHash)
//      })
//      .find { pb =>
//        ByteVector(kec256(BlockHeader.getEncodedWithoutNonce(pb.block.header))) == powHeaderHash
//      }
//
//  /**
//    * This function returns the block currently being mined block with highest timestamp
//    */
//  def getPending: F[Option[PendingBlock]] =
//    for {
//      p <- cache.get
//      b = if (p.isEmpty) None
//      else Some(p.maxBy(_.block.header.unixTimestamp))
//    } yield b
//
//  //returns maximal limit to be able to include as many transactions as possible
//  private def calculateGasLimit(parentGas: BigInt): BigInt = {
//    val GasLimitBoundDivisor: Int = 1024
//
//    val gasLimitDifference = parentGas / GasLimitBoundDivisor
//    parentGas + gasLimitDifference - 1
//  }
//
//  def buildMPT[V](entities: List[V], cv: Codec[V]): F[ByteVector] = {
//    for {
//      mpt <- MPTrieStore.inMemory[F, Int, V]
//      _ <- entities.zipWithIndex.map { case (v, k) => mpt.put(k, v)}.sequence
//      root <- mpt.getRootHash
//    } yield root
//  }
//}
//
//trait BlockTimestampProvider {
//  def getEpochSecond: Long
//}
//
//object DefaultBlockTimestampProvider extends BlockTimestampProvider {
//  override def getEpochSecond: Long = Instant.now.getEpochSecond
//}
//
//object BlockGenerator {
//  case class InvalidOmmers(reason: OmmersError) extends BlockPreparationError
//
//}
