package jbok.core.ledger

import cats.Foldable
import cats.data.NonEmptyList
import cats.effect.{ConcurrentEffect, Sync, Timer}
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.{BlockChainConfig, TxPoolConfig}
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock._
import jbok.core.messages.{BlockHash, NewBlock, NewBlockHashes}
import jbok.core.models.UInt256._
import jbok.core.models._
import jbok.core.peer.{Peer, PeerManager, PeerSelectStrategy, Response}
import jbok.core.pool.{BlockPool, OmmerPool, TxPool}
import jbok.core.store.namespaces
import jbok.common.ByteUtils
import jbok.core.validators.{BlockValidator, TransactionValidator}
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.evm._
import jbok.persistent.KeyValueDB
import scodec.Codec
import scodec.bits.ByteVector

import scala.concurrent.duration._

case class BlockExecResult[F[_]](
    world: WorldState[F],
    gasUsed: BigInt = 0,
    receipts: List[Receipt] = Nil
)

case class TxExecResult[F[_]](
    world: WorldState[F],
    gasUsed: BigInt,
    logs: List[TxLogEntry],
    vmReturnData: ByteVector,
    vmError: Option[ProgramError]
)

sealed trait BlockImportResult
object BlockImportResult {
  case class Succeed(imported: List[Block], tds: List[BigInt]) extends BlockImportResult
  case class Failed(errs: NonEmptyList[Throwable])             extends BlockImportResult
  case object Pooled                                           extends BlockImportResult
}

case class BlockExecutor[F[_]](
    config: BlockChainConfig,
    consensus: Consensus[F],
    peerManager: PeerManager[F],
    vm: VM,
    txValidator: TransactionValidator[F],
    blockValidator: BlockValidator[F],
    txPool: TxPool[F],
    ommerPool: OmmerPool[F]
)(implicit F: Sync[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("BlockExecutor")

  val history: History[F] = consensus.history

  val blockPool: BlockPool[F] = consensus.pool

  def handleBlock(typedBlock: TypedBlock) = typedBlock match {
    case received: ReceivedBlock[F]    => handleReceivedBlock(received)
    case requested: RequestedBlocks[F] => handleRequestedBlocks(requested)
    case mined: MinedBlock             => handleMinedBlock(mined)
    case _                             => F.unit
  }

  def importBlocks(blocks: List[Block]): F[Unit] =
    blocks.traverse(importBlock).void

  def importBlock(block: Block): F[BlockImportResult] =
    for {
      best            <- history.getBestBlock
      currentTd       <- history.getTotalDifficultyByHash(best.header.hash).map(_.get)
      consensusResult <- consensus.verifyHeader(block.header)
      importResult <- consensusResult match {
        case Consensus.Commit     => importBlockToTop(block, best.header.number, currentTd)
        case Consensus.Stash      => blockPool.addBlock(block, best.header.number).map(_ => BlockImportResult.Pooled)
        case Consensus.Discard(e) => F.pure(BlockImportResult.Failed(e))
      }
    } yield importResult

  def handleReceivedBlock(received: ReceivedBlock[F]): F[List[Block]] =
    for {
      _ <- received.peer.markBlock(received.block.header.hash)
      result <- importBlock(received.block).flatMap[List[Block]] {
        case BlockImportResult.Succeed(imported, _) =>
          updateTxAndOmmerPools(imported, Nil).as(imported)

        case BlockImportResult.Failed(e) =>
          F.delay(log.warn(e.toList.mkString("\n"))).as(Nil)

        case BlockImportResult.Pooled =>
          updateTxAndOmmerPools(List(received.block), Nil).as(List(received.block))
      }
    } yield result

  private[jbok] def handleRequestedBlocks(requested: RequestedBlocks[F]): F[Unit] =
    importBlocks(requested.blocks)

  private[jbok] def handleMinedBlock(mined: MinedBlock): F[Unit] =
    for {
      best            <- history.getBestBlock
      currentTd       <- history.getTotalDifficultyByHash(best.header.hash).map(_.get)
      consensusResult <- consensus.verifyHeader(mined.block.header)
      _ <- consensusResult match {
        case Consensus.Commit =>
          history.putBlockAndReceipts(mined.block, mined.receipts, currentTd + mined.block.header.difficulty, true)

        case Consensus.Stash =>
          blockPool.addBlock(mined.block, best.header.number)

        case Consensus.Discard(reasons) =>
          F.delay(log.warn(s"discard reasons: ${reasons.map(_.getMessage).toList.mkString("\n")}"))
      }
    } yield ()

  def executePendingBlock(pending: PendingBlock): F[ExecutedBlock[F]] =
    for {
      best             <- history.getBestBlock
      currentTd        <- history.getTotalDifficultyByHash(best.header.hash).map(_.get)
      (result, txs)    <- executeTransactions(pending.block, shortCircuit = false)
      transactionsRoot <- calcMerkleRoot(txs)
      receiptsRoot     <- calcMerkleRoot(result.receipts)
      header = pending.block.header.copy(
        transactionsRoot = transactionsRoot,
        stateRoot = result.world.stateRootHash,
        receiptsRoot = receiptsRoot,
        logsBloom = ByteUtils.or(BloomFilter.EmptyBloomFilter +: result.receipts.map(_.logsBloomFilter): _*),
        gasUsed = result.gasUsed
      )
      body = pending.block.body.copy(transactionList = txs)
      executed = ExecutedBlock(
        Block(header, body),
        result.world,
        result.gasUsed,
        result.receipts,
        currentTd + header.difficulty
      )
      postProcessed <- consensus.postProcess(executed)
      persisted     <- postProcessed.world.persisted
      header2 = header.copy(stateRoot = persisted.stateRootHash)
      block2  = executed.block.copy(header = header2)
    } yield executed.copy(block = block2, world = persisted)

  def executeBlocks(blocks: List[Block], parentTd: BigInt, shortCircuit: Boolean): F[List[ExecutedBlock[F]]] =
    blocks match {
      case block :: tail =>
        executeBlock(block, parentTd, shortCircuit).attempt.flatMap {
          case Right(x @ ExecutedBlock(block, world, gasUsed, receipts, td)) =>
            log.debug(s"${block.tag} execution succeed")
            for {
              _ <- history.putBlockAndReceipts(block, receipts, td, asBestBlock = true)
              _ = log.debug(s"${block.tag} saved as the best block")
              executedBlocks <- executeBlocks(tail, td, shortCircuit)
            } yield x :: executedBlocks

          case Left(error) =>
            log.error(error)(s"${block.tag} execution failed")
            F.raiseError(error)
        }

      case Nil =>
        F.pure(Nil)
    }

  def simulateTransaction(stx: SignedTransaction, blockHeader: BlockHeader): F[TxExecResult[F]] = {
    val stateRoot = blockHeader.stateRoot

    val gasPrice      = UInt256(stx.gasPrice)
    val gasLimit      = stx.gasLimit
    val vmConfig      = EvmConfig.forBlock(blockHeader.number, config)
    val senderAddress = getSenderAddress(stx, blockHeader.number)

    for {
      _       <- txValidator.validateSimulateTx(stx)
      world1  <- history.getWorldState(config.accountStartNonce, Some(stateRoot))
      world2  <- updateSenderAccountBeforeExecution(senderAddress, stx, world1)
      context <- prepareProgramContext(stx, senderAddress, blockHeader, world2, vmConfig)
      result  <- runVM(stx, context, vmConfig)
      totalGasToRefund = calcTotalGasToRefund(stx, result)
    } yield {
      log.debug(
        s"""SimulateTransaction(${stx.hash.toHex.take(7)}) execution end with ${result.error} error.
           |result.returnData: ${result.returnData.toHex}
           |gas refund: ${totalGasToRefund}""".stripMargin
      )

      TxExecResult(result.world, gasLimit - totalGasToRefund, result.logs, result.returnData, result.error)
    }
  }

  def binarySearchGasEstimation(stx: SignedTransaction, blockHeader: BlockHeader): F[BigInt] = {
    val lowLimit  = EvmConfig.forBlock(blockHeader.number, config).feeSchedule.G_transaction
    val highLimit = stx.gasLimit

    if (highLimit < lowLimit)
      F.pure(highLimit)
    else {
      binaryChop(lowLimit, highLimit)(gasLimit =>
        simulateTransaction(stx.copy(gasLimit = gasLimit), blockHeader).map(_.vmError))
    }
  }

  ////////////////////////////////
  ////////////////////////////////

  private def importBlockToTop(block: Block, bestNumber: BigInt, currentTd: BigInt): F[BlockImportResult] =
    for {
      topBlockHash <- blockPool.addBlock(block, bestNumber).map(_.get.hash)
      topBlocks    <- blockPool.getBranch(topBlockHash, dequeue = true)
      _ = log.debug(s"execute top blocks: ${topBlocks.map(_.tag)}")
      result <- executeBlocks(topBlocks, currentTd, true).attempt.map {
        case Left(e) =>
          BlockImportResult.Failed(NonEmptyList.of(e))

        case Right(executedBlocks) =>
          val totalDifficulties = executedBlocks
            .foldLeft(List(currentTd)) { (tds, b) =>
              (tds.head + b.block.header.difficulty) :: tds
            }
            .reverse
            .tail

          BlockImportResult.Succeed(executedBlocks.map(_.block), totalDifficulties)
      }
    } yield result

  private def executeBlock(block: Block, parentTd: BigInt, shortCircuit: Boolean): F[ExecutedBlock[F]] =
    for {
      (result, _) <- executeTransactions(block, shortCircuit)
      executed = ExecutedBlock(block, result.world, result.gasUsed, result.receipts, parentTd + block.header.difficulty)
      postProcessed <- consensus.postProcess(executed)
      persisted     <- postProcessed.world.persisted
      _ <- blockValidator.postExecuteValidate(
        postProcessed.block.header,
        persisted.stateRootHash,
        postProcessed.receipts,
        postProcessed.gasUsed
      )
    } yield postProcessed

  private def executeTransactions(block: Block,
                                  shortCircuit: Boolean): F[(BlockExecResult[F], List[SignedTransaction])] =
    for {
      parentStateRoot <- history.getBlockHeaderByHash(block.header.parentHash).map(_.map(_.stateRoot))
      world <- history.getWorldState(
        config.accountStartNonce,
        parentStateRoot,
        false
      )
      start  <- T.clock.realTime(MILLISECONDS)
      result <- executeTransactions(block.body.transactionList, block.header, world, shortCircuit)
      end    <- T.clock.realTime(MILLISECONDS)
      _ = log.debug(s"execute ${block.body.transactionList.length} transactions in ${end - start}ms")
    } yield result

  private[jbok] def executeTransactions(
      transactions: List[SignedTransaction],
      header: BlockHeader,
      world: WorldState[F],
      shortCircuit: Boolean,
      accGas: BigInt = 0,
      accReceipts: List[Receipt] = Nil,
      accExecuted: List[SignedTransaction] = Nil,
  ): F[(BlockExecResult[F], List[SignedTransaction])] = transactions match {
    case Nil =>
      F.pure(BlockExecResult(world = world, gasUsed = accGas, receipts = accReceipts), accExecuted)

    case stx :: tail =>
      for {
        result <- executeTransaction(stx, header, world, accGas).attempt.flatMap {
          case Left(e) =>
            log.warn(e)(s"execute ${stx} error")
            if (shortCircuit) {
              F.raiseError[(BlockExecResult[F], List[SignedTransaction])](e)
            } else {
              executeTransactions(
                tail,
                header,
                world,
                shortCircuit,
                accGas,
                accReceipts,
                accExecuted
              )
            }

          case Right(txResult) =>
            val stateRootHash = txResult.world.stateRootHash
            val receipt = Receipt(
              postTransactionStateHash = stateRootHash,
              cumulativeGasUsed = accGas + txResult.gasUsed,
              logsBloomFilter = BloomFilter.create(txResult.logs),
              logs = txResult.logs
            )
            executeTransactions(
              tail,
              header,
              txResult.world,
              shortCircuit,
              accGas + txResult.gasUsed,
              accReceipts :+ receipt,
              accExecuted :+ stx
            )
        }
      } yield result
  }

  private[jbok] def executeTransaction(
      stx: SignedTransaction,
      header: BlockHeader,
      world: WorldState[F],
      accGas: BigInt
  ): F[TxExecResult[F]] = {
    val gasPrice      = UInt256(stx.gasPrice)
    val gasLimit      = stx.gasLimit
    val vmConfig      = EvmConfig.forBlock(header.number, config)
    val senderAddress = getSenderAddress(stx, header.number)

    for {
      (senderAccount, worldForTx) <- world
        .getAccountOpt(senderAddress)
        .map(a => (a, world))
        .getOrElse((Account.empty(UInt256.Zero), world.putAccount(senderAddress, Account.empty(UInt256.Zero))))
      upfrontCost = calculateUpfrontCost(stx)
      _                    <- txValidator.validate(stx, senderAccount, header, upfrontCost, accGas)
      checkpointWorldState <- updateSenderAccountBeforeExecution(senderAddress, stx, worldForTx)
      context              <- prepareProgramContext(stx, senderAddress, header, checkpointWorldState, vmConfig)
      result               <- runVM(stx, context, vmConfig)
      resultWithErrorHandling = if (result.error.isDefined) {
        //Rollback to the world before transfer was done if an error happened
        result.copy(world = checkpointWorldState, addressesToDelete = Set.empty, logs = Nil)
      } else {
        result
      }

      totalGasToRefund         = calcTotalGasToRefund(stx, resultWithErrorHandling)
      executionGasToPayToMiner = gasLimit - totalGasToRefund
      refundGasFn              = pay(senderAddress, (totalGasToRefund * gasPrice).toUInt256) _
      payMinerForGasFn         = pay(Address(header.beneficiary), (executionGasToPayToMiner * gasPrice).toUInt256) _
      deleteAccountsFn         = deleteAccounts(resultWithErrorHandling.addressesToDelete) _
      deleteTouchedAccountsFn  = deleteEmptyTouchedAccounts _
      worldAfterPayments <- refundGasFn(resultWithErrorHandling.world) >>= payMinerForGasFn
      world2             <- (deleteAccountsFn(worldAfterPayments) >>= deleteTouchedAccountsFn).flatMap(_.persisted)
    } yield {
      log.trace(
        s"""Transaction(${stx.hash.toHex.take(7)}) execution end with ${result.error} error.
           |return data: ${result.returnData.toHex}
           |gas refund: ${totalGasToRefund}, gas paid to miner: ${executionGasToPayToMiner}""".stripMargin
      )
      TxExecResult(world2, executionGasToPayToMiner, resultWithErrorHandling.logs, result.returnData, result.error)
    }
  }

//  private def payReward(block: Block, world: WorldState[F]): F[WorldState[F]] = {
//    def getAccountToPay(address: Address, ws: WorldState[F]): F[Account] =
//      ws.getAccountOpt(address)
//        .getOrElse(Account.empty(config.accountStartNonce))
//
//    val minerAddress = Address(block.header.beneficiary)
//
//    for {
//      minerAccount <- getAccountToPay(minerAddress, world)
//      minerReward  <- consensus.calcBlockMinerReward(block.header.number, block.body.uncleNodesList.size)
//      afterMinerReward = world.putAccount(minerAddress, minerAccount.increaseBalance(UInt256(minerReward)))
//      _                = log.debug(s"block(${block.header.number}) reward of $minerReward paid to miner $minerAddress")
//      world <- Foldable[List].foldLeftM(block.body.uncleNodesList, afterMinerReward) { (ws, ommer) =>
//        val ommerAddress = Address(ommer.beneficiary)
//        for {
//          account     <- getAccountToPay(ommerAddress, ws)
//          ommerReward <- consensus.calcOmmerMinerReward(block.header.number, ommer.number)
//          _ = log.debug(s"block(${block.header.number}) reward of $ommerReward paid to ommer $ommerAddress")
//        } yield ws.putAccount(ommerAddress, account.increaseBalance(UInt256(ommerReward)))
//      }
//    } yield world
//  }

  private def updateSenderAccountBeforeExecution(
      senderAddress: Address,
      stx: SignedTransaction,
      world: WorldState[F]
  ): F[WorldState[F]] =
    world.getAccount(senderAddress).map { account =>
      world.putAccount(senderAddress, account.increaseBalance(-calculateUpfrontGas(stx)).increaseNonce())
    }

  private def prepareProgramContext(
      stx: SignedTransaction,
      sender: Address,
      blockHeader: BlockHeader,
      world: WorldState[F],
      config: EvmConfig
  ): F[ProgramContext[F]] =
    if (stx.isContractInit) {
      for {
        address <- world.createAddress(sender)
        _ = log.debug(s"contract address: ${address}")
        conflict <- world.nonEmptyCodeOrNonceAccount(address)
        code = if (conflict) ByteVector(INVALID.code) else stx.payload
        world1 <- world
          .initialiseAccount(address)
          .flatMap(_.transfer(sender, address, UInt256(stx.value)))
      } yield ProgramContext(stx, sender, address, Program(code), blockHeader, world1, config)
    } else {
      for {
        world1 <- world.transfer(sender, stx.receivingAddress, UInt256(stx.value))
        code   <- world1.getCode(stx.receivingAddress)
      } yield ProgramContext(stx, sender, stx.receivingAddress, Program(code), blockHeader, world1, config)
    }

  private def runVM(stx: SignedTransaction, context: ProgramContext[F], config: EvmConfig): F[ProgramResult[F]] =
    for {
      result <- vm.run(context)
    } yield {
      if (stx.isContractInit && result.error.isEmpty)
        saveNewContract(context.env.ownerAddr, result, config)
      else
        result
    }

  private def saveNewContract(address: Address, result: ProgramResult[F], config: EvmConfig): ProgramResult[F] = {
    val contractCode    = result.returnData
    val codeDepositCost = config.calcCodeDepositCost(contractCode)

    val maxCodeSizeExceeded = config.maxCodeSize.exists(codeSizeLimit => contractCode.size > codeSizeLimit)
    val codeStoreOutOfGas   = result.gasRemaining < codeDepositCost

    log.debug(
      s"codeDepositCost: ${codeDepositCost}, maxCodeSizeExceeded: ${maxCodeSizeExceeded}, codeStoreOutOfGas: ${codeStoreOutOfGas}")
    if (maxCodeSizeExceeded || codeStoreOutOfGas) {
      // Code size too big or code storage causes out-of-gas with exceptionalFailedCodeDeposit enabled
      log.debug("putcode outofgas")
      result.copy(error = Some(OutOfGas))
    } else {
      // Code storage succeeded
      log.debug(s"address putcode: ${address}")
      result.copy(gasRemaining = result.gasRemaining - codeDepositCost,
                  world = result.world.putCode(address, result.returnData))
    }
  }

  private def pay(address: Address, value: UInt256)(world: WorldState[F]): F[WorldState[F]] =
    F.ifM(world.isZeroValueTransferToNonExistentAccount(address, value))(
      ifTrue = world.pure[F],
      ifFalse = for {
        account <- world.getAccountOpt(address).getOrElse(Account.empty(config.accountStartNonce))
      } yield world.putAccount(address, account.increaseBalance(value)).touchAccounts(address)
    )

  private def getSenderAddress(stx: SignedTransaction, number: BigInt): Address = {
    val addrOpt =
      if (number >= config.eip155BlockNumber)
        stx.senderAddress(Some(config.chainId))
      else
        stx.senderAddress(None)
    addrOpt.getOrElse(Address.empty)
  }

  private def calculateUpfrontCost(stx: SignedTransaction): UInt256 =
    UInt256(calculateUpfrontGas(stx) + stx.value)

  private def calculateUpfrontGas(stx: SignedTransaction): UInt256 =
    UInt256(stx.gasLimit * stx.gasPrice)

  private def calcTotalGasToRefund(stx: SignedTransaction, result: ProgramResult[F]): BigInt =
    if (result.error.isEmpty || result.error.contains(RevertOp)) {
      val gasUsed = stx.gasLimit - result.gasRemaining
      // remaining gas plus some allowance
      result.gasRemaining + (gasUsed / 2).min(result.gasRefund)
    } else {
      0
    }

  private def deleteAccounts(addressesToDelete: Set[Address])(worldStateProxy: WorldState[F]): F[WorldState[F]] =
    addressesToDelete.foldLeft(worldStateProxy) { case (world, address) => world.delAccount(address) }.pure[F]

  private def deleteEmptyTouchedAccounts(world: WorldState[F]): F[WorldState[F]] = {
    def deleteEmptyAccount(world: WorldState[F], address: Address): F[WorldState[F]] =
      Sync[F].ifM(world.getAccountOpt(address).exists(_.isEmpty(config.accountStartNonce)))(
        ifTrue = world.delAccount(address).pure[F],
        ifFalse = world.pure[F]
      )

    Foldable[List]
      .foldLeftM(world.touchedAccounts.toList, world)((world, address) => deleteEmptyAccount(world, address))
      .map(_.clearTouchedAccounts)
  }

  private def binaryChop[Error](min: BigInt, max: BigInt)(f: BigInt => F[Option[Error]]): F[BigInt] = {
    assert(min <= max)
    if (min == max)
      F.pure(max)
    else {
      val mid           = min + (max - min) / 2
      val possibleError = f(mid)
      F.ifM(possibleError.map(_.isEmpty))(ifTrue = binaryChop(min, mid)(f), ifFalse = binaryChop(mid + 1, max)(f))
    }
  }

  private[jbok] def calcMerkleRoot[V: Codec](entities: List[V]): F[ByteVector] =
    for {
      db   <- KeyValueDB.inmem[F]
      mpt  <- MerklePatriciaTrie[F](namespaces.empty, db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put[Int, V](k, v, namespaces.empty) }.sequence
      root <- mpt.getRootHash
    } yield root

  private[jbok] def updateTxAndOmmerPools(blocksAdded: List[Block], blocksRemoved: List[Block]): F[Unit] =
    for {
      _ <- ommerPool.addOmmers(blocksRemoved.headOption.toList.map(_.header))
      _ <- blocksRemoved.map(_.body.transactionList).traverse(txs => txPool.addTransactions(txs))
      _ <- blocksAdded.map { block =>
        ommerPool.removeOmmers(block.header :: block.body.uncleNodesList) *>
          txPool.removeTransactions(block.body.transactionList)
      }.sequence
    } yield ()
}

object BlockExecutor {
  def apply[F[_]](
      config: BlockChainConfig,
      consensus: Consensus[F],
      peerManager: PeerManager[F],
  )(implicit F: ConcurrentEffect[F], T: Timer[F]): F[BlockExecutor[F]] =
    for {
      txPool    <- TxPool[F](TxPoolConfig(), peerManager)
      ommerPool <- OmmerPool[F](consensus.history)
      vm             = new VM
      txValidator    = new TransactionValidator[F](config)
      blockValidator = new BlockValidator[F]
    } yield BlockExecutor(config, consensus, peerManager, vm, txValidator, blockValidator, txPool, ommerPool)
}
