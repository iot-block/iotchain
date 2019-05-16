package jbok.app.service

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.HistoryConfig
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.mining.BlockMiner
import jbok.core.models._
import jbok.core.pool.{BlockPool, OmmerPool, TxPool}
import jbok.sdk.api.{BlockParam, CallTx, PublicAPI}
import scodec.bits.ByteVector

final class PublicService[F[_]](
    historyConfig: HistoryConfig,
    history: History[F],
    executor: BlockExecutor[F],
    txPool: TxPool[F],
    ommerPool: OmmerPool[F],
    blockPool: BlockPool[F],
    miner: BlockMiner[F]
)(implicit F: Sync[F])
    extends PublicAPI[F] {

  override def bestBlockNumber: F[BigInt] =
    history.getBestBlockNumber

  override def getBlockTransactionCountByHash(blockHash: ByteVector): F[Option[Int]] =
    history.getBlockBodyByHash(blockHash).map(_.map(_.transactionList.length))

  override def getBlockByHash(blockHash: ByteVector): F[Option[Block]] =
    history.getBlockByHash(blockHash)

  override def getBlockByNumber(blockNumber: BigInt): F[Option[Block]] =
    history.getBlockByNumber(blockNumber)

  override def getTransactionByHashFromHistory(txHash: ByteVector): F[Option[SignedTransaction]] = {
    val inBlock = for {
      loc   <- OptionT(history.getTransactionLocation(txHash))
      block <- OptionT(history.getBlockByHash(loc.blockHash))
      stx   <- OptionT.fromOption[F](block.body.transactionList.lift(loc.txIndex))
    } yield stx

    inBlock.value
  }

  override def getTransactionsInTxPool(address: Address): F[List[SignedTransaction]] =
    txPool.getPendingTransactions.map(_.keys.toList.filter(_.senderAddress.exists(_ == address)))

  override def getTransactionByHashFromTxPool(txHash: ByteVector): F[Option[SignedTransaction]] =
    txPool.getPendingTransactions.map(_.keys.toList.find(_.hash == txHash))

  override def getTransactionReceipt(txHash: ByteVector): F[Option[Receipt]] = {
    val r = for {
      loc      <- OptionT(history.getTransactionLocation(txHash))
      block    <- OptionT(history.getBlockByHash(loc.blockHash))
      _        <- OptionT.fromOption[F](block.body.transactionList.lift(loc.txIndex))
      receipts <- OptionT(history.getReceiptsByHash(loc.blockHash))
      receipt  <- OptionT.fromOption[F](receipts.lift(loc.txIndex))
    } yield receipt

    r.value
  }

  override def getTransactionByBlockHashAndIndexRequest(blockHash: ByteVector, txIndex: Int): F[Option[SignedTransaction]] = {
    val x = for {
      block <- OptionT(history.getBlockByHash(blockHash))
      stx   <- OptionT.fromOption[F](block.body.transactionList.lift(txIndex))
    } yield stx

    x.value
  }

  override def getOmmerByBlockHashAndIndex(blockHash: ByteVector, uncleIndex: Int): F[Option[BlockHeader]] = {
    val x = for {
      block <- OptionT(history.getBlockByHash(blockHash))
      uncle <- OptionT.fromOption[F](block.body.ommerList.lift(uncleIndex))
    } yield uncle

    x.value
  }

  override def getOmmerByBlockNumberAndIndex(blockParam: BlockParam, uncleIndex: Int): F[Option[BlockHeader]] = {
    val x = for {
      block <- OptionT.liftF(resolveBlock(blockParam))
      uncle <- OptionT.fromOption[F](block.flatMap(_.body.ommerList.lift(uncleIndex)))
    } yield uncle

    x.value
  }

  override def getGasPrice: F[BigInt] = {
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

  override def getEstimatedNonce(address: Address): F[BigInt] =
    for {
      pending <- txPool.getPendingTransactions
      latestNonceOpt = scala.util
        .Try(pending.collect {
          case (stx, _) if stx.senderAddress contains address => stx.nonce
        }.max)
        .toOption
      bn              <- history.getBestBlockNumber
      currentNonceOpt <- history.getAccount(address, bn).map(_.map(_.nonce.toBigInt))
      defaultNonce     = historyConfig.accountStartNonce.toBigInt
      maybeNextTxNonce = latestNonceOpt.map(_ + 1).getOrElse(defaultNonce) max currentNonceOpt.getOrElse(defaultNonce)
    } yield maybeNextTxNonce

  override def sendSignedTransaction(stx: SignedTransaction): F[ByteVector] =
    txPool.addOrUpdateTransaction(stx) *> F.pure(stx.hash)

  override def sendRawTransaction(data: ByteVector): F[ByteVector] =
    for {
      stx <- F.fromEither(data.asEither[SignedTransaction])
      _   <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash

  override def call(callTx: CallTx, blockParam: BlockParam): F[ByteVector] =
    for {
      (stx, block) <- doCall(callTx, blockParam)
      txResult     <- executor.simulateTransaction(stx, callTx.from.getOrElse(Address.empty), block.header)
    } yield txResult.vmReturnData

  override def getEstimatedGas(callTx: CallTx, blockParam: BlockParam): F[BigInt] =
    for {
      (stx, block) <- doCall(callTx, blockParam)
      gas          <- executor.binarySearchGasEstimation(stx, callTx.from.getOrElse(Address.empty), block.header)
    } yield gas

  override def getCode(address: Address, blockParam: BlockParam): F[ByteVector] =
    for {
      block <- resolveBlock(blockParam)
      world <- history.getWorldState(historyConfig.accountStartNonce, block.map(_.header.stateRoot))
      code  <- world.getCode(address)
    } yield code

  override def getOmmerCountByBlockNumber(blockParam: BlockParam): F[Int] =
    for {
      block <- resolveBlock(blockParam)
    } yield block.map(_.body.ommerList.length).getOrElse(0)

  override def getOmmerCountByBlockHash(blockHash: ByteVector): F[Int] =
    for {
      body <- history.getBlockBodyByHash(blockHash)
    } yield body.map(_.ommerList.length).getOrElse(-1)

  override def getBlockTransactionCountByNumber(blockParam: BlockParam): F[Int] =
    resolveBlock(blockParam).map(_.map(_.body.transactionList.length).getOrElse(0))

  override def getTransactionByBlockNumberAndIndexRequest(
      blockParam: BlockParam,
      txIndex: Int
  ): F[Option[SignedTransaction]] =
    for {
      block <- resolveBlock(blockParam)
      tx = block.flatMap(_.body.transactionList.lift(txIndex))
    } yield tx

  override def getAccount(address: Address, blockParam: BlockParam): F[Account] =
    for {
      account <- resolveAccount(address, blockParam)
    } yield account

  override def getBalance(address: Address, blockParam: BlockParam): F[BigInt] =
    for {
      account <- resolveAccount(address, blockParam)
    } yield account.balance.toBigInt

  override def getStorageAt(address: Address, position: BigInt, blockParam: BlockParam): F[ByteVector] =
    for {
      account <- resolveAccount(address, blockParam)
      storage <- history.getStorage(account.storageRoot, position)
    } yield storage

  override def getTransactionCount(address: Address, blockParam: BlockParam): F[BigInt] =
    for {
      account <- resolveAccount(address, blockParam)
    } yield account.nonce.toBigInt

  /////////////////////
  /////////////////////

  private def emptyError = new Exception("unexpected empty option")

  private[jbok] def doCall[A](callTx: CallTx, blockParam: BlockParam): F[(SignedTransaction, Block)] =
    for {
      stx      <- prepareTransaction(callTx, blockParam)
      blockOpt <- resolveBlock(blockParam)
      block    <- F.fromOption(blockOpt, emptyError)
    } yield (stx, block)

  private[jbok] def prepareTransaction(callTx: CallTx, blockParam: BlockParam): F[SignedTransaction] =
    for {
      gasLimit <- getGasLimit(callTx, blockParam)
      tx = Transaction(0, callTx.gasPrice, gasLimit, callTx.to, callTx.value, callTx.data)
    } yield SignedTransaction(tx, 0.toByte, ByteVector(0), ByteVector(0))

  private[jbok] def getGasLimit(callTx: CallTx, blockParam: BlockParam): F[BigInt] =
    callTx.gas match {
      case Some(gas) => gas.pure[F]
      case None      => resolveBlock(blockParam).map(_.map(_.header.gasLimit).getOrElse(BigInt(0)))
    }

  private[jbok] def resolveAccount(address: Address, blockParam: BlockParam): F[Account] =
    for {
      blockOpt <- resolveBlock(blockParam)
      account <- blockOpt match {
        case Some(block) =>
          history
            .getAccount(address, block.header.number)
            .map(_.getOrElse(Account.empty(historyConfig.accountStartNonce)))
        case None =>
          F.pure(Account.empty(historyConfig.accountStartNonce))
      }
    } yield account

  private[jbok] def resolveBlock(blockParam: BlockParam): F[Option[Block]] = {
    def getBlock(number: BigInt): F[Option[Block]] =
      if (number < 0) F.pure(None)
      else history.getBlockByNumber(number)

    blockParam match {
      case BlockParam.WithNumber(blockNumber) => getBlock(blockNumber)
      case BlockParam.Earliest                => getBlock(0)
      case BlockParam.Latest                  => history.getBestBlockNumber >>= getBlock
    }
  }
}
