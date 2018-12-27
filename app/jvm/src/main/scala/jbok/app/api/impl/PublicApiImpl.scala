package jbok.app.api.impl

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.HistoryConfig
import jbok.core.mining.BlockMiner
import jbok.core.models._
import jbok.sdk.api.{BlockParam, CallTx, PublicAPI}
import scodec.bits.ByteVector

final class PublicApiImpl(
    historyConfig: HistoryConfig,
    miner: BlockMiner[IO]
) extends PublicAPI[IO] {

  val history   = miner.history
  val txPool    = miner.executor.txPool
  val ommerPool = miner.executor.ommerPool
  val blockPool = miner.executor.blockPool

  override def bestBlockNumber: IO[BigInt] =
    history.getBestBlockNumber

  override def getBlockTransactionCountByHash(blockHash: ByteVector): IO[Option[Int]] =
    history.getBlockBodyByHash(blockHash).map(_.map(_.transactionList.length))

  override def getBlockByHash(blockHash: ByteVector): IO[Option[Block]] =
    history.getBlockByHash(blockHash)

  override def getBlockByNumber(blockNumber: BigInt): IO[Option[Block]] =
    history.getBlockByNumber(blockNumber)

  override def getTransactionByHash(txHash: ByteVector): IO[Option[SignedTransaction]] = {
    val pending = OptionT(txPool.getPendingTransactions.map(_.keys.toList.find(_.hash == txHash)))
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

  override def getOmmerByBlockHashAndIndex(blockHash: ByteVector, uncleIndex: Int): IO[Option[BlockHeader]] = {
    val x = for {
      block <- OptionT(history.getBlockByHash(blockHash))
      uncle <- OptionT.fromOption[IO](block.body.ommerList.lift(uncleIndex))
    } yield uncle

    x.value
  }

  override def getOmmerByBlockNumberAndIndex(blockParam: BlockParam, uncleIndex: Int): IO[Option[BlockHeader]] = {
    val x = for {
      block <- OptionT.liftF(resolveBlock(blockParam))
      uncle <- OptionT.fromOption[IO](block.flatMap(_.body.ommerList.lift(uncleIndex)))
    } yield uncle

    x.value
  }

  override def getGasPrice: IO[BigInt] = {
    val blockDifference = BigInt(30)
    for {
      bestBlock <- history.getBestBlockNumber
      gasPrices <- ((bestBlock - blockDifference) to bestBlock)
        .filter(_ >= BigInt(0))
        .toList
        .traverse(history.getBlockByNumber)
        .map(_.flatten.flatMap(_.body.transactionList).map(_.gasPrice))
      gasPrice = if (gasPrices.nonEmpty) {
        gasPrices.sum / gasPrices.length
      } else {
        BigInt(0)
      }
    } yield gasPrice
  }

  override def isMining: IO[Boolean] = miner.haltWhenTrue.get.map(!_)

  override def sendRawTransaction(data: ByteVector): IO[ByteVector] =
    for {
      stx <- IO(data.asOpt[SignedTransaction].get)
      _   <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash

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
      world <- history.getWorldState(historyConfig.accountStartNonce, block.map(_.header.stateRoot))
      code  <- world.getCode(address)
    } yield code

  override def getOmmerCountByBlockNumber(blockParam: BlockParam): IO[Int] =
    for {
      block <- resolveBlock(blockParam)
    } yield block.map(_.body.ommerList.length).getOrElse(0)

  override def getOmmerCountByBlockHash(blockHash: ByteVector): IO[Int] =
    for {
      body <- history.getBlockBodyByHash(blockHash)
    } yield body.map(_.ommerList.length).getOrElse(-1)

  override def getBlockTransactionCountByNumber(blockParam: BlockParam): IO[Int] =
    resolveBlock(blockParam).map(_.map(_.body.transactionList.length).getOrElse(0))

  override def getTransactionByBlockNumberAndIndexRequest(
      blockParam: BlockParam,
      txIndex: Int
  ): IO[Option[SignedTransaction]] =
    for {
      block <- resolveBlock(blockParam)
      tx = block.flatMap(_.body.transactionList.lift(txIndex))
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
      storage <- history.getStorage(account.storageRoot, position)
    } yield storage

  override def getTransactionCount(address: Address, blockParam: BlockParam): IO[BigInt] =
    for {
      account <- resolveAccount(address, blockParam)
    } yield account.nonce.toBigInt

  override def getAccountTransactions(address: Address,
                                      fromBlock: BigInt,
                                      toBlock: BigInt): IO[List[SignedTransaction]] = {
    def collectTxs: PartialFunction[SignedTransaction, SignedTransaction] = {
      case stx if stx.senderAddress.nonEmpty && stx.senderAddress.get == address => stx
      case stx if stx.receivingAddress == address                                => stx
    }
    for {
      blocks <- (fromBlock to toBlock).toList.filter(_ >= BigInt(0)).traverse(history.getBlockByNumber)
      stxsFromBlock = blocks.collect {
        case Some(block) => block.body.transactionList.collect(collectTxs)
      }.flatten
      pendingStxs <- txPool.getPendingTransactions
      stxsFromPool = pendingStxs.keys.toList.collect(collectTxs)
    } yield stxsFromBlock ++ stxsFromPool
  }

  /////////////////////
  /////////////////////

  private[jbok] def doCall[A](callTx: CallTx, blockParam: BlockParam): IO[(SignedTransaction, Block)] =
    for {
      stx   <- prepareTransaction(callTx, blockParam)
      block <- resolveBlock(blockParam)
    } yield (stx, block.get)

  private[jbok] def prepareTransaction(callTx: CallTx, blockParam: BlockParam): IO[SignedTransaction] =
    for {
      gasLimit <- getGasLimit(callTx, blockParam)
      tx = Transaction(0, callTx.gasPrice, gasLimit, callTx.to, callTx.value, callTx.data)
    } yield SignedTransaction(tx, 0.toByte, ByteVector(0), ByteVector(0))

  private[jbok] def getGasLimit(callTx: CallTx, blockParam: BlockParam): IO[BigInt] =
    if (callTx.gas.isDefined) {
      callTx.gas.get.pure[IO]
    } else {
      resolveBlock(BlockParam.Latest).map(_.map(_.header.gasLimit).getOrElse(BigInt(0)))
    }

  private[jbok] def resolveAccount(address: Address, blockParam: BlockParam): IO[Account] =
    for {
      blockOpt <- resolveBlock(blockParam)
      account <- if (blockOpt.isEmpty) {
        IO.pure(Account.empty(historyConfig.accountStartNonce))
      } else {
        history
          .getAccount(address, blockOpt.get.header.number)
          .map(_.getOrElse(Account.empty(historyConfig.accountStartNonce)))
      }
    } yield account

  private[jbok] def resolveBlock(blockParam: BlockParam): IO[Option[Block]] = {
    def getBlock(number: BigInt): IO[Option[Block]] =
      number.some.filter(_ >= BigInt(0)).traverse[IO, Block](n => history.getBlockByNumber(n).map(_.get))

    blockParam match {
      case BlockParam.WithNumber(blockNumber) => getBlock(blockNumber)
      case BlockParam.Earliest                => getBlock(0)
      case BlockParam.Latest                  => history.getBestBlockNumber >>= getBlock
    }
  }
}

object PublicApiImpl {
  def apply(historyConfig: HistoryConfig, miner: BlockMiner[IO]): PublicAPI[IO] =
    new PublicApiImpl(historyConfig, miner)
}
