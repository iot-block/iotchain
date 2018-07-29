package jbok.core.api.impl

import java.time.Duration
import java.util.Date

import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
import jbok.core.api._
import jbok.core.configs.{BlockChainConfig, MiningConfig}
import jbok.core.consensus.Ethash
import jbok.core.keystore.KeyStore
import jbok.core.ledger.{Ledger, OmmersPool}
import jbok.core.mining.BlockGenerator
import jbok.core.models._
import jbok.core.{BlockChain, TxPool}
import jbok.crypto.signature.CryptoSignature
import scodec.Codec
import scodec.bits.ByteVector

class PublicApiImpl[F[_]](
    blockChain: BlockChain[F],
    blockChainConfig: BlockChainConfig,
    ommersPool: OmmersPool[F],
    txPool: TxPool[F],
    ledger: Ledger[F],
    blockGenerator: BlockGenerator[F],
    keyStore: KeyStore[F],
    filterManager: FilterManager[F],
    miningConfig: MiningConfig,
    version: Int,
    hashRate: Ref[F, Map[ByteVector, (BigInt, Date)]],
    lastActive: Ref[F, Option[Date]]
)(implicit F: Sync[F])
    extends PublicAPI[F] {
  implicit def toRight[A](fa: F[A]): R[A] = fa.map(a => Right(a))

  override def protocolVersion: R[String] =
    f"0x${version}%x".pure[F]

  override def bestBlockNumber: R[BigInt] =
    blockChain.getBestBlockNumber

  override def getBlockTransactionCountByHash(blockHash: ByteVector): R[Option[Int]] = {
    val count = blockChain.getBlockBodyByHash(blockHash).map(_.map(_.transactionList.length))
    count
  }

  override def getBlockByHash(blockHash: ByteVector): R[Option[Block]] =
    blockChain.getBlockByHash(blockHash)

  override def getBlockByNumber(blockNumber: BigInt): R[Option[Block]] =
    blockChain.getBlockByNumber(blockNumber)

  override def getTransactionByHash(txHash: ByteVector): R[Option[SignedTransaction]] = {
    val pending = OptionT(txPool.getPendingTransactions.map(_.map(_.stx).find(_.hash == txHash)))
    val inBlock = for {
      loc <- OptionT(blockChain.getTransactionLocation(txHash))
      block <- OptionT(blockChain.getBlockByHash(loc.blockHash))
      stx <- OptionT.fromOption[F](block.body.transactionList.lift(loc.txIndex))
    } yield stx

    pending.orElseF(inBlock.value).value
  }

  override def getTransactionReceipt(txHash: ByteVector): R[Option[Receipt]] =
    (for {
      loc <- OptionT(blockChain.getTransactionLocation(txHash))
      block <- OptionT(blockChain.getBlockByHash(loc.blockHash))
      stx <- OptionT.fromOption[F](block.body.transactionList.lift(loc.txIndex))
      receipts <- OptionT(blockChain.getReceiptsByHash(loc.blockHash))
      receipt <- OptionT.fromOption[F](receipts.lift(loc.txIndex))
    } yield receipt).value

  override def getTransactionByBlockHashAndIndexRequest(blockHash: ByteVector,
                                                        txIndex: Int): R[Option[SignedTransaction]] =
    (for {
      block <- OptionT(blockChain.getBlockByHash(blockHash))
      stx <- OptionT.fromOption[F](block.body.transactionList.lift(txIndex))
    } yield stx).value

  override def getUncleByBlockHashAndIndex(blockHash: ByteVector, uncleIndex: Int): R[Option[BlockHeader]] =
    (for {
      block <- OptionT(blockChain.getBlockByHash(blockHash))
      uncle <- OptionT.fromOption[F](block.body.uncleNodesList.lift(uncleIndex))
    } yield uncle).value

  override def getUncleByBlockNumberAndIndex(blockParam: BlockParam, uncleIndex: Int): R[Option[BlockHeader]] =
    (for {
      block <- OptionT.liftF(resolveBlock(blockParam))
      uncle <- OptionT.fromOption[F](block.body.uncleNodesList.lift(uncleIndex))
    } yield uncle).value

  override def submitHashRate(hr: BigInt, id: ByteVector): R[Boolean] = {
    val rate = for {
      _ <- reportActive
      now = new Date
      _ <- hashRate.modify(m => removeObsoleteHashrates(now, m + (id -> (hr, now))))
    } yield true

    rate
  }

  override def getGasPrice: R[BigInt] = {
    val blockDifference = 30
    val gasPrice = for {
      bestBlock <- blockChain.getBestBlockNumber
      gasPrices <- ((bestBlock - blockDifference) to bestBlock).toList
        .traverse(blockChain.getBlockByNumber)
        .map(_.flatten.flatMap(_.body.transactionList).map(_.tx.gasPrice))
      gasPrice = if (gasPrices.nonEmpty) {
        gasPrices.sum / gasPrices.length
      } else {
        BigInt(0)
      }
    } yield gasPrice

    gasPrice
  }

  override def getMining: R[Boolean] = {
    val isMining = lastActive
      .modify(e =>
        e.filter(time =>
          Duration.between(time.toInstant, (new Date).toInstant).toMillis < miningConfig.activeTimeout.toMillis))
      .map(_.now.isDefined)

    isMining
  }

  override def getHashRate: R[BigInt] = {
    val rate = hashRate.modify(m => removeObsoleteHashrates(new Date, m)).map(_.now.map(_._2._1).sum)
    rate
  }

  override def getWork: R[GetWorkResponse] = {
    val resp = for {
      _ <- reportActive
      bestBlock <- blockChain.getBestBlock
      ommers <- ommersPool.getOmmers(bestBlock.header.number + 1)
      txs <- txPool.getPendingTransactions
      resp <- blockGenerator
        .generateBlockForMining(bestBlock, txs.map(_.stx), ommers, miningConfig.coinbase)
        .value
        .flatMap {
          case Right(pb) =>
            GetWorkResponse(
              powHeaderHash = pb.block.header.hashWithoutNonce,
              dagSeed = Ethash.seed(Ethash.epoch(pb.block.header.number.toLong)),
              target = ByteVector((BigInt(2).pow(256) / pb.block.header.difficulty).toByteArray)
            ).pure[F]
          case Left(err) =>
            F.raiseError[GetWorkResponse](new RuntimeException("unable to prepare block"))
        }
    } yield resp

    resp
  }

  override def getCoinbase: R[Address] = {
    val addr = miningConfig.coinbase.pure[F]
    addr
  }

  override def submitWork(nonce: ByteVector, powHeaderHash: ByteVector, mixHash: ByteVector): R[Boolean] = {
    val succeed = for {
      _ <- reportActive
      bn <- blockChain.getBestBlockNumber
      succeed <- blockGenerator.getPrepared(powHeaderHash).map {
        case Some(pendingBlock) if bn <= pendingBlock.block.header.number =>
          true
        case _ =>
          false
      }
    } yield succeed
    succeed
  }

  override def syncing: R[Option[SyncingStatus]] = {
    val status = for {
      currentBlock <- blockChain.getBestBlockNumber
      highestBlock <- blockChain.getEstimatedHighestBlock
      startingBlock <- blockChain.getSyncStartingBlock
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

    status
  }

  override def sendRawTransaction(data: ByteVector): R[ByteVector] = {
    val stx = Codec.decode[SignedTransaction](data.bits).require.value
    val txHash = for {
      _ <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash
    txHash
  }

  override def call(callTx: CallTx, blockParam: BlockParam): R[ByteVector] = {
    val returnData = for {
      (stx, block) <- doCall(callTx, blockParam)
      txResult <- ledger.simulateTransaction(stx, block.header)
    } yield txResult.vmReturnData
    returnData
  }

  override def estimateGas(callTx: CallTx, blockParam: BlockParam): R[BigInt] = {
    val gas = for {
      (stx, block) <- doCall(callTx, blockParam)
      gas <- ledger.binarySearchGasEstimation(stx, block.header)
    } yield gas

    gas
  }

  override def getCode(address: Address, blockParam: BlockParam): R[ByteVector] = {
    val code = for {
      block <- resolveBlock(blockParam)
      world <- blockChain.getWorldStateProxy(block.header.number,
        blockChainConfig.accountStartNonce,
        Some(block.header.stateRoot))
      code <- world.getCode(address)
    } yield code

    code
  }

  override def getUncleCountByBlockNumber(blockParam: BlockParam): R[Int] = {
    val count = for {
      block <- resolveBlock(blockParam)
    } yield block.body.uncleNodesList.length
    count
  }

  override def getUncleCountByBlockHash(blockHash: ByteVector): R[Int] = {
    val count = for {
      body <- blockChain.getBlockBodyByHash(blockHash)
    } yield body.map(_.uncleNodesList.length).getOrElse(-1)
    count
  }

  override def getBlockTransactionCountByNumber(blockParam: BlockParam): R[Int] = {
    val count = resolveBlock(blockParam).map(_.body.transactionList.length)
    count
  }

  override def getTransactionByBlockNumberAndIndexRequest(
      blockParam: BlockParam,
      txIndex: Int
  ): R[Option[SignedTransaction]] = {
    val tx = for {
      block <- resolveBlock(blockParam)
      tx = block.body.transactionList.lift(txIndex)
    } yield tx

    tx
  }

  override def getBalance(address: Address, blockParam: BlockParam): R[BigInt] = {
    val balance = for {
      account <- resolveAccount(address, blockParam)
    } yield account.balance.toBigInt

    balance
  }

  override def getStorageAt(address: Address, position: BigInt, blockParam: BlockParam): R[ByteVector] = {
    val storage = for {
      account <- resolveAccount(address, blockParam)
      storage <- blockChain.getAccountStorageAt(account.storageRoot, position)
    } yield storage

    storage
  }

  override def getTransactionCount(address: Address, blockParam: BlockParam): R[BigInt] = {
    val count = for {
      account <- resolveAccount(address, blockParam)
    } yield account.nonce.toBigInt

    count
  }

  override def newFilter(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): R[BigInt] = {
    val id = filterManager.newLogFilter(fromBlock, toBlock, address, topics)
    id
  }

  override def newBlockFilter: R[BigInt] = {
    val id = filterManager.newBlockFilter
    id
  }

  override def newPendingTransactionFilter: R[BigInt] = {
    val id = filterManager.newPendingTxFilter
    id
  }

  override def uninstallFilter(filterId: BigInt): R[Boolean] = {
    val succeed = filterManager.uninstallFilter(filterId).map(_ => true)
    succeed
  }

  override def getFilterChanges(filterId: BigInt): R[FilterChanges] = {
    val changes = filterManager.getFilterChanges(filterId)
    changes
  }

  override def getFilterLogs(filterId: BigInt): R[FilterLogs] = {
    val logs = filterManager.getFilterLogs(filterId)
    logs
  }

  override def getLogs(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): R[LogFilterLogs] = {
    val logs = filterManager
      .getLogs(LogFilter(0, fromBlock, toBlock, address, topics))
      .map(LogFilterLogs)

    logs
  }

  override def getAccountTransactions(address: Address, fromBlock: BigInt, toBlock: BigInt): R[List[Transaction]] = {
    val numBlocksToSearch = toBlock - fromBlock
    ???
  }

  /////////////////////
  /////////////////////

  private[jbok] def doCall[A](callTx: CallTx, blockParam: BlockParam): F[(SignedTransaction, Block)] =
    for {
      stx <- prepareTransaction(callTx, blockParam)
      block <- resolveBlock(blockParam)
    } yield (stx, block)

  private[jbok] def prepareTransaction(callTx: CallTx, blockParam: BlockParam) =
    for {
      gasLimit <- getGasLimit(callTx, blockParam)
      from <- OptionT
        .fromOption[F](callTx.from.map(Address.apply))
        .getOrElseF(
          EitherT(keyStore.listAccounts).getOrElse(Nil).map(_.head)
        )
      to = callTx.to.map(Address.apply)
      tx = Transaction(0, callTx.gasPrice, gasLimit, to, callTx.value, callTx.data)
      fakeSignature = CryptoSignature(0, 0, Some(0.toByte))
    } yield SignedTransaction(tx, fakeSignature, from)

  private[jbok] def getGasLimit(callTx: CallTx, blockParam: BlockParam): F[BigInt] =
    if (callTx.gas.isDefined) {
      F.pure(callTx.gas.get)
    } else {
      resolveBlock(BlockParam.Latest).map(_.header.gasLimit)
    }

  private[jbok] def removeObsoleteHashrates(now: Date,
                                            rates: Map[ByteVector, (BigInt, Date)]): Map[ByteVector, (BigInt, Date)] =
    rates.filter {
      case (_, (_, reported)) =>
        Duration.between(reported.toInstant, now.toInstant).toMillis < miningConfig.activeTimeout.toMillis
    }

  private[jbok] def reportActive: F[Unit] = {
    val now = new Date()
    lastActive.modify(_ => Some(now)).void
  }

  private[jbok] def resolveAccount(address: Address, blockParam: BlockParam): F[Account] =
    for {
      block <- resolveBlock(blockParam)
      account <- blockChain
        .getAccount(address, block.header.number)
        .map(_.getOrElse(Account.empty(blockChainConfig.accountStartNonce)))
    } yield account

  private[jbok] def resolveBlock(blockParam: BlockParam): F[Block] = {
    def getBlock(number: BigInt): F[Block] = blockChain.getBlockByNumber(number).map(_.get)

    blockParam match {
      case BlockParam.WithNumber(blockNumber) => getBlock(blockNumber)
      case BlockParam.Earliest                => getBlock(0)
      case BlockParam.Latest                  => blockChain.getBestBlockNumber >>= getBlock
      case BlockParam.Pending =>
        OptionT(blockGenerator.getPending.map(_.map(_.block))).getOrElseF(resolveBlock(BlockParam.Latest))
    }
  }
}
