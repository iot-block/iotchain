package jbok.core.api

import cats.effect.{Concurrent, Fiber, Timer}
import cats.implicits._
import fs2.async.Ref
import jbok.core.configs.FilterConfig
import jbok.core.keystore.KeyStore
import jbok.core.ledger.BloomFilter
import jbok.core.mining.BlockGenerator
import jbok.core.models.{Address, Block, Receipt}
import jbok.core.{BlockChain, TxPool, TxPoolConfig}
import scodec.bits.ByteVector

import scala.util.Random

case class TxLog(
    logIndex: BigInt,
    transactionIndex: BigInt,
    transactionHash: ByteVector,
    blockHash: ByteVector,
    blockNumber: BigInt,
    address: Address,
    data: ByteVector,
    topics: List[ByteVector]
)

//case class Filter(
//    fromBlock: Option[BlockParam],
//    toBlock: Option[BlockParam],
//    address: Option[Address],
//    topics: List[List[ByteVector]]
//)

sealed trait FilterChanges
case class LogFilterChanges(logs: List[TxLog]) extends FilterChanges
case class BlockFilterChanges(blockHashes: List[ByteVector]) extends FilterChanges
case class PendingTransactionFilterChanges(txHashes: List[ByteVector]) extends FilterChanges

sealed trait FilterLogs
case class LogFilterLogs(logs: List[TxLog]) extends FilterLogs
case class BlockFilterLogs(blockHashes: List[ByteVector]) extends FilterLogs
case class PendingTransactionFilterLogs(txHashes: List[ByteVector]) extends FilterLogs

sealed trait Filter {
  def id: BigInt
}
case class LogFilter(
    id: BigInt,
    fromBlock: Option[BlockParam],
    toBlock: Option[BlockParam],
    address: Option[Address],
    topics: List[List[ByteVector]]
) extends Filter
case class BlockFilter(id: BigInt) extends Filter
case class PendingTransactionFilter(id: BigInt) extends Filter

class FilterManager[F[_]](
    blockChain: BlockChain[F],
    blockGenerator: BlockGenerator[F],
    keyStore: KeyStore[F],
    txPool: TxPool[F],
    filterConfig: FilterConfig,
    txPoolConfig: TxPoolConfig,
    filters: Ref[F, Map[BigInt, Filter]],
    lastCheckBlocks: Ref[F, Map[BigInt, BigInt]],
    lastCheckTimestamps: Ref[F, Map[BigInt, Long]],
    filterTimeouts: Ref[F, Map[BigInt, Fiber[F, Unit]]]
)(implicit F: Concurrent[F], T: Timer[F]) {
  val maxBlockHashesChanges = 256

  def newLogFilter(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): F[BigInt] =
    addFilterAndSendResponse(
      LogFilter(
        generateId(),
        fromBlock,
        toBlock,
        address,
        topics
      ))

  def newBlockFilter: F[BigInt] =
    addFilterAndSendResponse(BlockFilter(generateId()))

  def newPendingTxFilter: F[BigInt] =
    addFilterAndSendResponse(PendingTransactionFilter(generateId()))

  def uninstallFilter(id: BigInt): F[Unit] =
    for {
      _ <- filters.modify(_ - id)
      _ <- lastCheckBlocks.modify(_ - id)
      _ <- lastCheckTimestamps.modify(_ - id)
      _ <- filterTimeouts.get.flatMap(_.get(id).map(_.cancel).getOrElse(F.unit))
      _ <- filterTimeouts.modify(_ - id)
    } yield ()

  def getFilterLogs(filterId: BigInt): F[FilterLogs] =
    for {
      filterOpt <- filters.get.map(_.get(filterId))
      bn <- blockChain.getBestBlockNumber
      _ <- if (filterOpt.isDefined) {
        lastCheckBlocks.modify(_ + (filterId -> bn)) *>
          lastCheckTimestamps.modify(_ + (filterId -> System.currentTimeMillis()))
      } else {
        F.unit
      }
      _ <- resetTimeout(filterId)
      r <- filterOpt match {
        case Some(logFilter: LogFilter) =>
          getLogs(logFilter).map(LogFilterLogs)

        case Some(_: BlockFilter) =>
          F.pure(BlockFilterLogs(Nil))

        case Some(_: PendingTransactionFilter) =>
          txPool.getPendingTransactions.map(xs => PendingTransactionFilterLogs(xs.map(_.stx.hash)))

        case None =>
          F.pure(LogFilterLogs(Nil))
      }
    } yield r

  def getFilterChanges(id: BigInt): F[FilterChanges] =
    for {
      bestBlockNumber <- blockChain.getBestBlockNumber
      lastCheckBlock <- lastCheckBlocks.get.map(_.getOrElse(id, bestBlockNumber))
      lastCheckTimestamp <- lastCheckTimestamps.get.map(_.getOrElse(id, System.currentTimeMillis()))
      filterOpt <- filters.get.map(_.get(id))
      _ <- if (filterOpt.isDefined) {
        lastCheckBlocks.modify(_ + (id -> bestBlockNumber)) *>
          lastCheckTimestamps.modify(_ + (id -> System.currentTimeMillis()))
      } else {
        F.unit
      }

      _ <- resetTimeout(id)

      changes <- filterOpt match {
        case Some(logFilter: LogFilter) =>
          getLogs(logFilter, Some(lastCheckBlock + 1)).map(LogFilterChanges)

        case Some(_: BlockFilter) =>
          getBlockHashesAfter(lastCheckBlock).map(hashes => BlockFilterChanges(hashes.takeRight(maxBlockHashesChanges)))

        case Some(_: PendingTransactionFilter) =>
          txPool.getPendingTransactions.map(xs => {
            val filtered = xs.filter(_.addTimestamp > lastCheckTimestamp)
            PendingTransactionFilterChanges(filtered.map(_.stx.hash))
          })

        case None =>
          F.pure(LogFilterChanges(Nil))
      }
    } yield changes

  def getLogs(filter: LogFilter, startingBlockNumber: Option[BigInt] = None): F[List[TxLog]] = {
    val bytesToCheckInBloomFilter =
      filter.address.map(a => List(a.bytes)).getOrElse(Nil) ++ filter.topics.flatten

    def recur(currentBlockNumber: BigInt, toBlockNumber: BigInt, logsSoFar: List[TxLog]): F[List[TxLog]] =
      if (currentBlockNumber > toBlockNumber) {
        F.pure(logsSoFar)
      } else {
        blockChain.getBlockHeaderByNumber(currentBlockNumber).flatMap {
          case Some(header)
              if bytesToCheckInBloomFilter.isEmpty || BloomFilter.containsAnyOf(header.logsBloom,
                                                                                bytesToCheckInBloomFilter) =>
            blockChain.getReceiptsByHash(header.hash).flatMap {
              case Some(receipts) =>
                blockChain
                  .getBlockBodyByHash(header.hash)
                  .map(_.get)
                  .flatMap(body => {
                    recur(
                      currentBlockNumber + 1,
                      toBlockNumber,
                      logsSoFar ++ getLogsFromBlock(filter, Block(header, body), receipts)
                    )
                  })
              case None => F.pure(logsSoFar)
            }
          case Some(_) => recur(currentBlockNumber + 1, toBlockNumber, logsSoFar)
          case None    => F.pure(logsSoFar)
        }
      }

    for {
      bestBlockNumber <- blockChain.getBestBlockNumber
      fromBlockNumber = startingBlockNumber.getOrElse(
        resolveBlockNumber(filter.fromBlock.getOrElse(BlockParam.Latest), bestBlockNumber))
      toBlockNumber = resolveBlockNumber(filter.toBlock.getOrElse(BlockParam.Latest), bestBlockNumber)
      logs <- recur(fromBlockNumber, toBlockNumber, Nil)
      l <- if (filter.toBlock.contains(BlockParam.Pending)) {
        blockGenerator.getPending
          .map(_.map(p => getLogsFromBlock(filter, p.block, p.receipts)).getOrElse(Nil))
          .map(xs => logs ++ xs)
      } else {
        F.pure(logs)
      }
    } yield l
  }

  /////////////////////////
  /////////////////////////

  private[jbok] def generateId(): BigInt = BigInt(Random.nextLong()).abs

  private[jbok] def addFilterAndSendResponse(filter: Filter): F[BigInt] =
    for {
      _ <- filters.modify(_ + (filter.id -> filter))
      bn <- blockChain.getBestBlockNumber
      _ <- lastCheckBlocks.modify(_ + (filter.id -> bn))
      _ <- lastCheckTimestamps.modify(_ + (filter.id -> System.currentTimeMillis()))
      _ <- resetTimeout(filter.id)
    } yield filter.id

  private[jbok] def resetTimeout(id: BigInt): F[Unit] =
    for {
      fiber <- filterTimeouts.get.map(_.get(id))
      _ <- fiber.map(_.cancel).getOrElse(F.unit)
      cancellable <- F.start(T.sleep(filterConfig.filterTimeout) *> uninstallFilter(id))
      _ <- filterTimeouts.modify(_ + (id -> cancellable))
    } yield ()

  private[jbok] def getLogsFromBlock(filter: LogFilter, block: Block, receipts: List[Receipt]): List[TxLog] = {
    val bytesToCheckInBloomFilter = filter.address.map(a => List(a.bytes)).getOrElse(Nil) ++ filter.topics.flatten

    receipts.zipWithIndex.foldLeft(Nil: List[TxLog]) {
      case (logsSoFar, (receipt, txIndex)) =>
        if (bytesToCheckInBloomFilter.isEmpty || BloomFilter.containsAnyOf(receipt.logsBloomFilter,
                                                                           bytesToCheckInBloomFilter)) {
          logsSoFar ++ receipt.logs.zipWithIndex
            .filter {
              case (log, _) =>
                filter.address.forall(_ == log.loggerAddress) && topicsMatch(log.logTopics, filter.topics)
            }
            .map {
              case (log, logIndex) =>
                val tx = block.body.transactionList(txIndex)
                TxLog(
                  logIndex = logIndex,
                  transactionIndex = txIndex,
                  transactionHash = tx.hash,
                  blockHash = block.header.hash,
                  blockNumber = block.header.number,
                  address = log.loggerAddress,
                  data = log.data,
                  topics = log.logTopics
                )
            }
        } else logsSoFar
    }
  }

  private[jbok] def topicsMatch(logTopics: List[ByteVector], filterTopics: List[List[ByteVector]]): Boolean =
    logTopics.size >= filterTopics.size &&
      (filterTopics zip logTopics).forall { case (filter, log) => filter.isEmpty || filter.contains(log) }

  private[jbok] def resolveBlockNumber(blockParam: BlockParam, bestBlockNumber: BigInt): BigInt =
    blockParam match {
      case BlockParam.WithNumber(blockNumber) => blockNumber
      case BlockParam.Earliest                => 0
      case BlockParam.Latest                  => bestBlockNumber
      case BlockParam.Pending                 => bestBlockNumber
    }

  private[jbok] def getBlockHashesAfter(blockNumber: BigInt): F[List[ByteVector]] = {
    def recur(bestBlockNumber: BigInt, currentBlockNumber: BigInt, hashesSoFar: List[ByteVector]): F[List[ByteVector]] =
      if (currentBlockNumber > bestBlockNumber) {
        F.pure(hashesSoFar)
      } else {
        blockChain.getBlockHeaderByNumber(currentBlockNumber).flatMap {
          case Some(header) => recur(bestBlockNumber, currentBlockNumber + 1, hashesSoFar :+ header.hash)
          case None         => F.pure(hashesSoFar)
        }
      }
    for {
      bestBlock <- blockChain.getBestBlockNumber
      hashes <- recur(bestBlock, blockNumber + 1, Nil)
    } yield hashes
  }
}

object FilterManager {
  def apply[F[_]: Concurrent](
      blockChain: BlockChain[F],
      blockGenerator: BlockGenerator[F],
      keyStore: KeyStore[F],
      txPool: TxPool[F],
      filterConfig: FilterConfig,
      txPoolConfig: TxPoolConfig
  )(implicit T: Timer[F]): F[FilterManager[F]] =
    for {
      filters <- fs2.async.refOf[F, Map[BigInt, Filter]](Map.empty)
      lastCheckBlocks <- fs2.async.refOf[F, Map[BigInt, BigInt]](Map.empty)
      lastCheckTimestamps <- fs2.async.refOf[F, Map[BigInt, Long]](Map.empty)
      filterTimeouts <- fs2.async.refOf[F, Map[BigInt, Fiber[F, Unit]]](Map.empty)
    } yield
      new FilterManager[F](
        blockChain,
        blockGenerator,
        keyStore,
        txPool,
        filterConfig,
        txPoolConfig,
        filters,
        lastCheckBlocks,
        lastCheckTimestamps,
        filterTimeouts
      )
}
