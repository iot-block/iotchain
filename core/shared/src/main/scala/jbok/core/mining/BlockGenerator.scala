package jbok.core.mining

import java.time.Instant

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
import jbok.codec.codecs._
import jbok.core.BlockChain
import jbok.core.configs.{BlockChainConfig, MiningConfig}
import jbok.core.ledger.{BlockResult, BloomFilter, DifficultyCalculator, Ledger}
import jbok.core.models._
import jbok.core.utils.ByteUtils
import jbok.core.validators.Validators
import jbok.crypto._
import jbok.crypto.authds.mpt.MPTrieStore
import scodec.Codec
import scodec.bits.ByteVector

trait BlockPreparationError
object BlockPreparationError {
  case class TxError(reason: String) extends BlockPreparationError
}

case class PendingBlock(block: Block, receipts: List[Receipt])
case class BlockPreparationResult[F[_]](block: Block, blockResult: BlockResult[F], stateRootHash: ByteVector)

class BlockGenerator[F[_]](
    blockchain: BlockChain[F],
    blockChainConfig: BlockChainConfig,
    miningConfig: MiningConfig,
    ledger: Ledger[F],
    validators: Validators[F],
    blockTimestampProvider: BlockTimestampProvider = DefaultBlockTimestampProvider,
    cache: Ref[F, List[PendingBlock]]
)(implicit F: Sync[F]) {
  private[this] val log = org.log4s.getLogger

  val difficulty = new DifficultyCalculator(blockChainConfig)

  def generateBlockForMining(
      parent: Block,
      transactions: List[SignedTransaction],
      ommers: List[BlockHeader],
      beneficiary: Address
  ): EitherT[F, String, PendingBlock] = {
    val blockNumber = parent.header.number + 1
    val parentHash = parent.header.hash
    val blockTimestamp = blockTimestampProvider.getEpochSecond
    val header: BlockHeader = prepareHeader(blockNumber, ommers, beneficiary, parent, blockTimestamp)
    val transactionsForBlock: List[SignedTransaction] = prepareTransactions(transactions, header.gasLimit)
    val body = BlockBody(transactionsForBlock, ommers)
    val block = Block(header, body)

    for {
      _ <- validators.ommersValidator.validate(parentHash, blockNumber, ommers, blockchain).leftMap(_.toString)
      prepared <- EitherT.liftF(ledger.prepareBlock(block))
      receiptsLogs = BloomFilter.EmptyBloomFilter +: prepared.blockResult.receipts.map(_.logsBloomFilter)
      bloomFilter = ByteUtils.or(receiptsLogs: _*)

      transactionsRoot <- EitherT.right(buildMPT[SignedTransaction](prepared.block.body.transactionList))
      stateRoot = prepared.stateRootHash
      receiptsRoot <- EitherT.right(buildMPT(prepared.blockResult.receipts))
      pendingBlock = PendingBlock(
        block.copy(
          header = block.header.copy(transactionsRoot = transactionsRoot,
                                     stateRoot = stateRoot,
                                     receiptsRoot = receiptsRoot,
                                     logsBloom = bloomFilter,
                                     gasUsed = prepared.blockResult.gasUsed),
          body = prepared.block.body
        ),
        prepared.blockResult.receipts
      )
      _ <- EitherT.right(cache.modify(xs => (pendingBlock :: xs).take(miningConfig.blockCacheSize)))
    } yield pendingBlock
  }

  private[jbok] def prepareTransactions(transactions: Seq[SignedTransaction], blockGasLimit: BigInt) = {
    val sortedTransactions = transactions
      .groupBy(_.senderAddress)
      .values
      .toList
      .flatMap { txsFromSender =>
        val ordered = txsFromSender
          .sortBy(-_.tx.gasPrice)
          .sortBy(_.tx.nonce)
          .foldLeft(Seq.empty[SignedTransaction]) {
            case (txs, tx) =>
              if (txs.exists(_.tx.nonce == tx.tx.nonce)) {
                txs
              } else {
                txs :+ tx
              }
          }
          .takeWhile(_.tx.gasLimit <= blockGasLimit)
        ordered.headOption.map(_.tx.gasPrice -> ordered)
      }
      .sortBy { case (gasPrice, _) => gasPrice }
      .reverse
      .flatMap { case (_, txs) => txs }

    val transactionsForBlock = sortedTransactions
      .scanLeft(BigInt(0), None: Option[SignedTransaction]) {
        case ((accumulatedGas, _), stx) => (accumulatedGas + stx.tx.gasLimit, Some(stx))
      }
      .collect { case (gas, Some(stx)) => (gas, stx) }
      .takeWhile { case (gas, _) => gas <= blockGasLimit }
      .map { case (_, stx) => stx }
    transactionsForBlock
  }

  private[jbok] def prepareHeader(
      blockNumber: BigInt,
      ommers: List[BlockHeader],
      beneficiary: Address,
      parent: Block,
      blockTimestamp: Long
  ) =
    BlockHeader(
      parentHash = parent.header.hash,
      ommersHash = codecList[BlockHeader].encode(ommers).require.bytes.kec256,
      beneficiary = beneficiary.bytes,
      stateRoot = ByteVector.empty,
      //we are not able to calculate transactionsRoot here because we do not know if they will fail
      transactionsRoot = ByteVector.empty,
      receiptsRoot = ByteVector.empty,
      logsBloom = ByteVector.empty,
      difficulty = difficulty.calculateDifficulty(blockNumber, blockTimestamp, parent.header),
      number = blockNumber,
      gasLimit = calculateGasLimit(parent.header.gasLimit),
      gasUsed = 0,
      unixTimestamp = blockTimestamp,
      extraData = blockChainConfig.daoForkConfig
        .flatMap(daoForkConfig => daoForkConfig.getExtraData(blockNumber))
        .getOrElse(miningConfig.headerExtraData),
      mixHash = ByteVector.empty,
      nonce = ByteVector.empty
    )

  def getPrepared(powHeaderHash: ByteVector): F[Option[PendingBlock]] =
    for {
      p <- cache.get
      n = p.filterNot(pb => pb.block.header.hash == powHeaderHash)
      _ <- cache.setSync(n)
      f = p.find(pb => pb.block.header.hash == powHeaderHash)
    } yield f

  /**
    * This function returns the block currently being mined block with highest timestamp
    */
  def getPending: F[Option[PendingBlock]] =
    for {
      p <- cache.get
      b = if (p.isEmpty) None
      else Some(p.maxBy(_.block.header.unixTimestamp))
    } yield b

  //returns maximal limit to be able to include as many transactions as possible
  private def calculateGasLimit(parentGas: BigInt): BigInt = {
    val GasLimitBoundDivisor: Int = 1024

    val gasLimitDifference = parentGas / GasLimitBoundDivisor
    parentGas + gasLimitDifference - 1
  }

  private[jbok] def buildMPT[V](entities: List[V])(implicit cv: Codec[V]): F[ByteVector] = {
    implicit val codecInt = scodec.codecs.uint16
    for {
      mpt <- MPTrieStore.inMemory[F, Int, V]
      _ <- entities.zipWithIndex.map { case (v, k) => mpt.put(k, v) }.sequence
      root <- mpt.getRootHash
    } yield root
  }
}

object BlockGenerator {
  def apply[F[_]: Sync](
      blockchain: BlockChain[F],
      blockChainConfig: BlockChainConfig,
      miningConfig: MiningConfig,
      ledger: Ledger[F],
      validators: Validators[F],
      blockTimestampProvider: BlockTimestampProvider = DefaultBlockTimestampProvider
  ): F[BlockGenerator[F]] =
    for {
      cache <- fs2.async.refOf[F, List[PendingBlock]](Nil)
    } yield
      new BlockGenerator[F](
        blockchain,
        blockChainConfig,
        miningConfig,
        ledger,
        validators,
        blockTimestampProvider,
        cache
      )
}

trait BlockTimestampProvider {
  def getEpochSecond: Long
}

object DefaultBlockTimestampProvider extends BlockTimestampProvider {
  override def getEpochSecond: Long = Instant.now.getEpochSecond
}

class FakeBlockTimestampProvider extends BlockTimestampProvider {
  private var timestamp = Instant.now.getEpochSecond

  def advance(seconds: Long): Unit = timestamp += seconds

  override def getEpochSecond: Long = timestamp
}
