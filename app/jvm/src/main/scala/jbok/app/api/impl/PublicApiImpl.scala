package jbok.app.api.impl

import java.time.Duration
import java.util.Date

import cats.data.OptionT
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.app.api._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.core.History
import jbok.core.config.Configs.{BlockChainConfig, MiningConfig}
import jbok.core.keystore.KeyStore
import jbok.core.mining.BlockMiner
import jbok.core.models._
import jbok.crypto.signature.CryptoSignature
import scodec.bits.ByteVector

class PublicApiImpl(
    blockChainConfig: BlockChainConfig,
    miningConfig: MiningConfig,
    miner: BlockMiner[IO],
    keyStore: KeyStore[IO],
    filterManager: FilterManager[IO],
    version: Int,
    hashRate: Ref[IO, Map[ByteVector, (BigInt, Date)]],
    lastActive: Ref[IO, Option[Date]]
) extends PublicAPI {

  val history   = miner.history
  val txPool    = miner.synchronizer.txPool
  val ommerPool = miner.synchronizer.ommerPool
  val blockPool = miner.synchronizer.executor.blockPool

  override def protocolVersion: IO[String] =
    IO.pure(f"0x${version}%x")

  override def bestBlockNumber: IO[BigInt] =
    history.getBestBlockNumber

  override def getBlockTransactionCountByHash(blockHash: ByteVector): IO[Option[Int]] =
    history.getBlockBodyByHash(blockHash).map(_.map(_.transactionList.length))

  override def getBlockByHash(blockHash: ByteVector): IO[Option[Block]] =
    history.getBlockByHash(blockHash)

  override def getBlockByNumber(blockNumber: BigInt): IO[Option[Block]] =
    history.getBlockByNumber(blockNumber)

  override def getTransactionByHash(txHash: ByteVector): IO[Option[SignedTransaction]] = {
    val pending = OptionT(txPool.getPendingTransactions.map(_.map(_.stx).find(_.hash == txHash)))
    val inBlock = for {
      loc   <- OptionT(history.getTransactionLocation(txHash))
      block <- OptionT(history.getBlockByHash(loc.blockHash))
      stx   <- OptionT.fromOption[IO](block.body.transactionList.lift(loc.txIndex))
    } yield stx

    pending.orElseF(inBlock.value).value
  }

  override def getTransactionReceipt(txHash: ByteVector): IO[Option[Receipt]] = {
    val r = for {
      loc      <- OptionT(history.getTransactionLocation(txHash))
      block    <- OptionT(history.getBlockByHash(loc.blockHash))
      stx      <- OptionT.fromOption[IO](block.body.transactionList.lift(loc.txIndex))
      receipts <- OptionT(history.getReceiptsByHash(loc.blockHash))
      receipt  <- OptionT.fromOption[IO](receipts.lift(loc.txIndex))
    } yield receipt

    r.value
  }

  override def getTransactionByBlockHashAndIndexRequest(blockHash: ByteVector,
                                                        txIndex: Int): IO[Option[SignedTransaction]] = {
    val x = for {
      block <- OptionT(history.getBlockByHash(blockHash))
      stx   <- OptionT.fromOption[IO](block.body.transactionList.lift(txIndex))
    } yield stx

    x.value
  }

  override def getUncleByBlockHashAndIndex(blockHash: ByteVector, uncleIndex: Int): IO[Option[BlockHeader]] = {
    val x = for {
      block <- OptionT(history.getBlockByHash(blockHash))
      uncle <- OptionT.fromOption[IO](block.body.uncleNodesList.lift(uncleIndex))
    } yield uncle

    x.value
  }

  override def getUncleByBlockNumberAndIndex(blockParam: BlockParam, uncleIndex: Int): IO[Option[BlockHeader]] = {
    val x = for {
      block <- OptionT.liftF(resolveBlock(blockParam))
      uncle <- OptionT.fromOption[IO](block.body.uncleNodesList.lift(uncleIndex))
    } yield uncle

    x.value
  }

  override def submitHashRate(hr: BigInt, id: ByteVector): IO[Boolean] =
    for {
      _ <- reportActive
      now = new Date
      _ <- hashRate.update(m => removeObsoleteHashrates(now, m + (id -> (hr, now))))
    } yield true

  override def getGasPrice: IO[BigInt] = {
    val blockDifference = BigInt(30)
    for {
      bestBlock <- history.getBestBlockNumber
      gasPrices <- ((bestBlock - blockDifference) to bestBlock).toList
        .traverse(history.getBlockByNumber)
        .map(_.flatten.flatMap(_.body.transactionList).map(_.gasPrice))
      gasPrice = if (gasPrices.nonEmpty) {
        gasPrices.sum / gasPrices.length
      } else {
        BigInt(0)
      }
    } yield gasPrice
  }

  override def getMining: IO[Boolean] = miner.isMining

  override def getHashRate: IO[BigInt] =
    ???
//    hashRate.update(m => removeObsoleteHashrates(new Date, m)).map(_.now.map(_._2._1).sum)

  override def getWork: IO[GetWorkResponse] = ???

  override def getCoinbase: IO[Address] =
    miningConfig.coinbase.pure[IO]

  override def submitWork(nonce: ByteVector, powHeaderHash: ByteVector, mixHash: ByteVector): IO[Boolean] = ???

  override def syncing: IO[Option[SyncingStatus]] =
    for {
      currentBlock  <- history.getBestBlockNumber
      highestBlock  <- history.getEstimatedHighestBlock
      startingBlock <- history.getSyncStartingBlock
    } yield {
      if (currentBlock < highestBlock) {
        Some(
          SyncingStatus(
            startingBlock,
            currentBlock,
            highestBlock
          ))
      } else {
        None
      }
    }

  override def sendRawTransaction(data: ByteVector): IO[ByteVector] = {
    val stx = RlpCodec.decode[SignedTransaction](data.bits).require.value
    val txHash = for {
      _ <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash
    txHash
  }

  override def call(callTx: CallTx, blockParam: BlockParam): IO[ByteVector] =
    for {
      (stx, block) <- doCall(callTx, blockParam)
      txResult     <- miner.executor.simulateTransaction(stx, block.header)
    } yield txResult.vmReturnData

  override def estimateGas(callTx: CallTx, blockParam: BlockParam): IO[BigInt] =
    for {
      (stx, block) <- doCall(callTx, blockParam)
      gas          <- miner.executor.binarySearchGasEstimation(stx, block.header)
    } yield gas

  override def getCode(address: Address, blockParam: BlockParam): IO[ByteVector] =
    for {
      block <- resolveBlock(blockParam)
      world <- history.getWorldStateProxy(block.header.number,
                                          blockChainConfig.accountStartNonce,
                                          Some(block.header.stateRoot))
      code <- world.getCode(address)
    } yield code

  override def getUncleCountByBlockNumber(blockParam: BlockParam): IO[Int] =
    for {
      block <- resolveBlock(blockParam)
    } yield block.body.uncleNodesList.length

  override def getUncleCountByBlockHash(blockHash: ByteVector): IO[Int] =
    for {
      body <- history.getBlockBodyByHash(blockHash)
    } yield body.map(_.uncleNodesList.length).getOrElse(-1)

  override def getBlockTransactionCountByNumber(blockParam: BlockParam): IO[Int] =
    resolveBlock(blockParam).map(_.body.transactionList.length)

  override def getTransactionByBlockNumberAndIndexRequest(
      blockParam: BlockParam,
      txIndex: Int
  ): IO[Option[SignedTransaction]] =
    for {
      block <- resolveBlock(blockParam)
      tx = block.body.transactionList.lift(txIndex)
    } yield tx

  override def getAccount(address: Address, blockParam: BlockParam): IO[Account] =
    for {
      account <- resolveAccount(address, blockParam)
    } yield account

  override def getBalance(address: Address, blockParam: BlockParam): IO[BigInt] =
    for {
      account <- resolveAccount(address, blockParam)
    } yield account.balance.toBigInt

  override def getStorageAt(address: Address, position: BigInt, blockParam: BlockParam): IO[ByteVector] =
    for {
      account <- resolveAccount(address, blockParam)
      storage <- history.getAccountStorageAt(account.storageRoot, position)
    } yield storage

  override def getTransactionCount(address: Address, blockParam: BlockParam): IO[BigInt] =
    for {
      account <- resolveAccount(address, blockParam)
    } yield account.nonce.toBigInt

  override def newFilter(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): IO[BigInt] =
    filterManager.newLogFilter(fromBlock, toBlock, address, topics)

  override def newBlockFilter: IO[BigInt] =
    filterManager.newBlockFilter

  override def newPendingTransactionFilter: IO[BigInt] =
    filterManager.newPendingTxFilter

  override def uninstallFilter(filterId: BigInt): IO[Boolean] =
    filterManager.uninstallFilter(filterId).map(_ => true)

  override def getFilterChanges(filterId: BigInt): IO[FilterChanges] =
    filterManager.getFilterChanges(filterId)

  override def getFilterLogs(filterId: BigInt): IO[FilterLogs] =
    filterManager.getFilterLogs(filterId)

  override def getLogs(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): IO[LogFilterLogs] =
    filterManager
      .getLogs(LogFilter(0, fromBlock, toBlock, address, topics))
      .map(LogFilterLogs)

  override def getAccountTransactions(address: Address,
                                      fromBlock: BigInt,
                                      toBlock: BigInt): IO[List[SignedTransaction]] = {
    val numBlocksToSearch = toBlock - fromBlock
    def collectTxs: PartialFunction[SignedTransaction, SignedTransaction] = {
      case stx if SignedTransaction.getSender(stx).nonEmpty && SignedTransaction.getSender(stx).get == address => stx
      case stx if stx.receivingAddress == address                                                              => stx
    }
    for {
      blocks <- (fromBlock to toBlock).toList.traverse(history.getBlockByNumber)
      stxsFromBlock = blocks.collect {
        case Some(block) => block.body.transactionList.collect(collectTxs)
      }.flatten
      pendingStxs <- txPool.getPendingTransactions
      stxsFromPool = pendingStxs.map(_.stx).collect(collectTxs)
    } yield stxsFromBlock ++ stxsFromPool
  }

  /////////////////////
  /////////////////////

  private[jbok] def doCall[A](callTx: CallTx, blockParam: BlockParam): IO[(SignedTransaction, Block)] =
    for {
      stx   <- prepareTransaction(callTx, blockParam)
      block <- resolveBlock(blockParam)
    } yield (stx, block)

  private[jbok] def prepareTransaction(callTx: CallTx, blockParam: BlockParam): IO[SignedTransaction] =
    for {
      gasLimit <- getGasLimit(callTx, blockParam)
      from <- OptionT
        .fromOption[IO](callTx.from.map(Address.apply))
        .getOrElseF(keyStore.listAccounts.map(_.head))
      to            = callTx.to.map(Address.apply)
      tx            = Transaction(0, callTx.gasPrice, gasLimit, to, callTx.value, callTx.data)
      fakeSignature = CryptoSignature(0, 0, 0.toByte)
    } yield SignedTransaction(tx, 0.toByte, ByteVector(0), ByteVector(0), from)

  private[jbok] def getGasLimit(callTx: CallTx, blockParam: BlockParam): IO[BigInt] =
    if (callTx.gas.isDefined) {
      callTx.gas.get.pure[IO]
    } else {
      resolveBlock(BlockParam.Latest).map(_.header.gasLimit)
    }

  private[jbok] def removeObsoleteHashrates(now: Date,
                                            rates: Map[ByteVector, (BigInt, Date)]): Map[ByteVector, (BigInt, Date)] =
    rates.filter {
      case (_, (_, reported)) =>
        Duration.between(reported.toInstant, now.toInstant).toMillis < miningConfig.activeTimeout.toMillis
    }

  private[jbok] def reportActive: IO[Unit] = {
    val now = new Date()
    lastActive.update(_ => Some(now))
  }

  private[jbok] def resolveAccount(address: Address, blockParam: BlockParam): IO[Account] =
    for {
      block <- resolveBlock(blockParam)
      account <- history
        .getAccount(address, block.header.number)
        .map(_.getOrElse(Account.empty(blockChainConfig.accountStartNonce)))
    } yield account

  private[jbok] def resolveBlock(blockParam: BlockParam): IO[Block] = {
    def getBlock(number: BigInt): IO[Block] = history.getBlockByNumber(number).map(_.get)

    blockParam match {
      case BlockParam.WithNumber(blockNumber) => getBlock(blockNumber)
      case BlockParam.Earliest                => getBlock(0)
      case BlockParam.Latest                  => history.getBestBlockNumber >>= getBlock
      case BlockParam.Pending =>
        ???
//        OptionT(blockGenerator.getUnconfirmed.map(_.map(_.block))).getOrElseF(resolveBlock(BlockParam.Latest))
    }
  }
}

object PublicApiImpl {
  def apply(
      blockChain: History[IO],
      blockChainConfig: BlockChainConfig,
      miningConfig: MiningConfig,
      miner: BlockMiner[IO],
      keyStore: KeyStore[IO],
      filterManager: FilterManager[IO],
      version: Int,
  ): IO[PublicAPI] =
    for {
      hashRate   <- Ref.of[IO, Map[ByteVector, (BigInt, Date)]](Map.empty)
      lastActive <- Ref.of[IO, Option[Date]](None)
    } yield {
      new PublicApiImpl(
        blockChainConfig,
        miningConfig: MiningConfig,
        miner,
        keyStore,
        filterManager,
        version: Int,
        hashRate,
        lastActive
      )
    }
}
