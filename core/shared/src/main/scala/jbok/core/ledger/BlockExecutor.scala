package jbok.core.ledger

import cats.Foldable
import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits._
import jbok.core.Configs.BlockChainConfig
import jbok.core.History
import jbok.core.consensus.{Consensus, ConsensusResult}
import jbok.core.models.UInt256._
import jbok.core.models._
import jbok.core.pool.BlockPool
import jbok.core.validators.{BlockValidator, CommonHeaderValidator, TransactionValidator}
import jbok.evm._
import scodec.bits.ByteVector

case class BlockResult[F[_]](worldState: WorldStateProxy[F], gasUsed: BigInt = 0, receipts: List[Receipt] = Nil)
case class TxResult[F[_]](
    world: WorldStateProxy[F],
    gasUsed: BigInt,
    logs: List[TxLogEntry],
    vmReturnData: ByteVector,
    vmError: Option[ProgramError]
)

sealed trait BlockImportResult
object BlockImportResult {
  case class Succeed(imported: List[Block], totalDifficulties: List[BigInt]) extends BlockImportResult
  case class Failed(error: Throwable)                                        extends BlockImportResult
  case object Pooled                                                         extends BlockImportResult
}

class BlockExecutor[F[_]](
    val config: BlockChainConfig,
    val history: History[F],
    val blockPool: BlockPool[F],
    val consensus: Consensus[F],
    val commonHeaderValidator: CommonHeaderValidator[F],
    val commonBlockValidator: BlockValidator[F],
    val txValidator: TransactionValidator[F],
    val vm: VM
)(implicit F: Sync[F]) {
  private[this] val log = org.log4s.getLogger

  def simulateTransaction(stx: SignedTransaction, blockHeader: BlockHeader): F[TxResult[F]] =
    ???

  def binarySearchGasEstimation(stx: SignedTransaction, blockHeader: BlockHeader): F[BigInt] =
    ???

  def importBlock(block: Block): F[BlockImportResult] =
    for {
      parent          <- history.getBestBlock
      currentTd       <- history.getTotalDifficultyByHash(parent.header.hash).map(_.get)
      consensusResult <- consensus.run(parent, block)
      importResult <- consensusResult match {
        case ConsensusResult.BlockInvalid(e) =>
          F.pure(BlockImportResult.Failed(e))
        case ConsensusResult.ImportToTop =>
          importBlockToTop(block, parent.header.number, currentTd)
        case ConsensusResult.Pooled =>
          blockPool.addBlock(block, parent.header.number).map(_ => BlockImportResult.Pooled)
      }
    } yield importResult

  def importBlockToTop(block: Block, bestBlockNumber: BigInt, currentTd: BigInt): F[BlockImportResult] =
    for {
      topBlockHash <- blockPool.addBlock(block, bestBlockNumber).map(_.get.hash)
      topBlocks    <- blockPool.getBranch(topBlockHash, dequeue = true)
      result <- executeBlocks(topBlocks, currentTd).attempt.map {
        case Left(e) =>
          BlockImportResult.Failed(e)

        case Right(importedBlocks) =>
          val totalDifficulties = importedBlocks
            .foldLeft(List(currentTd)) { (tds, b) =>
              (tds.head + b.header.difficulty) :: tds
            }
            .reverse
            .tail

          BlockImportResult.Succeed(importedBlocks, totalDifficulties)
      }
    } yield result

  def payReward(block: Block, world: WorldStateProxy[F]): F[WorldStateProxy[F]] = {
    def getAccountToPay(address: Address, ws: WorldStateProxy[F]): F[Account] =
      ws.getAccountOpt(address)
        .getOrElse(Account.empty(config.accountStartNonce))

    val minerAddress = Address(block.header.beneficiary)

    for {
      minerAccount <- getAccountToPay(minerAddress, world)
      minerReward  <- consensus.calcBlockMinerReward(block.header.number, block.body.uncleNodesList.size)
      afterMinerReward = world.putAccount(minerAddress, minerAccount.increaseBalance(UInt256(minerReward)))
      _                = log.info(s"Paying block-${block.header.number} reward of $minerReward to miner $minerAddress")
      world <- Foldable[List].foldLeftM(block.body.uncleNodesList, afterMinerReward) { (ws, ommer) =>
        val ommerAddress = Address(ommer.beneficiary)
        for {
          account     <- getAccountToPay(ommerAddress, ws)
          ommerReward <- consensus.calcOmmerMinerReward(block.header.number, ommer.number)
          _ = log.info(s"Paying block-${block.header.number} reward of $ommerReward to ommer $ommerAddress")
        } yield ws.putAccount(ommerAddress, account.increaseBalance(UInt256(ommerReward)))
      }
    } yield world
  }

  def preExecuteValidate(block: Block): F[Unit] =
    for {
      parentHeader <- commonHeaderValidator.validate(block.header)
      _            <- commonBlockValidator.validateHeaderAndBody(block)
      _            <- consensus.semanticValidate(parentHeader, block)
    } yield ()

  def executeBlock(block: Block, alreadyValidated: Boolean = false): F[List[Receipt]] =
    for {
      (result, _)    <- executeBlockTransactions(block)
      worldToPersist <- payReward(block, result.worldState)
      worldPersisted <- worldToPersist.persisted
      _ <- commonBlockValidator.postExecuteValidate(
        block.header,
        worldPersisted.stateRootHash,
        result.receipts,
        result.gasUsed
      )
    } yield result.receipts

  def executeBlockTransactions(block: Block,
                               shortCircuit: Boolean = true): F[(BlockResult[F], List[SignedTransaction])] =
    for {
      parentStateRoot <- history.getBlockHeaderByHash(block.header.parentHash).map(_.map(_.stateRoot))
      world <- history.getWorldStateProxy(
        block.header.number,
        config.accountStartNonce,
        parentStateRoot,
        EvmConfig.forBlock(block.header.number, config).noEmptyAccounts
      )
      result <- executeTransactions(block.body.transactionList, block.header, world, shortCircuit = shortCircuit)
    } yield result

  def executeBlocks(blocks: List[Block], parentTd: BigInt): F[List[Block]] = {
    log.info(s"start executing ${blocks.length} blocks")
    blocks match {
      case block :: tail =>
        executeBlock(block, alreadyValidated = true).attempt.flatMap {
          case Right(receipts) =>
            log.info(s"execute block ${block.header.hash.toHex.take(7)} succeed")
            val td = parentTd + block.header.difficulty
            for {
              _              <- history.save(block, receipts, td, saveAsBestBlock = true)
              executedBlocks <- executeBlocks(tail, td)
            } yield block :: executedBlocks

          case Left(error) =>
            log.info(s"execute block ${block.header.hash.toHex.take(7)} failed")
            F.raiseError(error)
        }

      case Nil =>
        F.pure(Nil)
    }
  }

  def executeTransactions(
      stxs: List[SignedTransaction],
      header: BlockHeader,
      world: WorldStateProxy[F],
      accGas: BigInt = 0,
      accReceipts: List[Receipt] = Nil,
      executed: List[SignedTransaction] = Nil,
      shortCircuit: Boolean = true
  ): F[(BlockResult[F], List[SignedTransaction])] = stxs match {
    case Nil =>
      F.pure(BlockResult(worldState = world, gasUsed = accGas, receipts = accReceipts), executed)

    case stx :: tail =>
      val senderAddress = getSenderAddress(stx, header.number)
      log.info(s"execute tx from ${senderAddress} to ${stx.receivingAddress}")
      for {
        (senderAccount, worldForTx) <- world
          .getAccountOpt(senderAddress)
          .map(a => (a, world))
          .getOrElse((Account.empty(UInt256.Zero), world.putAccount(senderAddress, Account.empty(UInt256.Zero))))

        upfrontCost = calculateUpfrontCost(stx)
        _ <- txValidator.validate(stx, senderAccount, header, upfrontCost, accGas)
        result <- executeTransaction(stx, header, worldForTx).attempt.flatMap {
          case Left(e) =>
            if (shortCircuit) {
              F.raiseError[(BlockResult[F], List[SignedTransaction])](e)
            } else {
              executeTransactions(
                tail,
                header,
                worldForTx,
                accGas,
                accReceipts,
                executed
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
              accGas + txResult.gasUsed,
              accReceipts :+ receipt,
              executed :+ stx
            )
        }

      } yield result
  }

  def executeTransaction(
      stx: SignedTransaction,
      header: BlockHeader,
      world: WorldStateProxy[F]
  ): F[TxResult[F]] = {
    log.info(s"Transaction(${stx.hash.toHex.take(7)}) execution start")
    val gasPrice      = UInt256(stx.gasPrice)
    val gasLimit      = stx.gasLimit
    val vmConfig      = EvmConfig.forBlock(header.number, config)
    val senderAddress = getSenderAddress(stx, header.number)

    for {
      checkpointWorldState <- updateSenderAccountBeforeExecution(senderAddress, stx, world)
      context              <- prepareProgramContext(stx, header, checkpointWorldState, vmConfig)
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
      persistStateFn           = WorldStateProxy.persist[F] _
      worldAfterPayments <- Kleisli(refundGasFn).andThen(payMinerForGasFn).run(resultWithErrorHandling.world)
      world2 <- Kleisli(deleteAccountsFn)
        .andThen(deleteTouchedAccountsFn)
        .andThen(persistStateFn)
        .run(worldAfterPayments)

    } yield {
      log.info(
        s"""Transaction(${stx.hash.toHex.take(7)}) execution end with ${result.error} error.
           |gas refund: ${totalGasToRefund}, gas paid to miner: ${executionGasToPayToMiner}""".stripMargin
      )

      TxResult(world2, executionGasToPayToMiner, resultWithErrorHandling.logs, result.returnData, result.error)
    }
  }

  ////////////////////////////////
  ////////////////////////////////

  private def updateSenderAccountBeforeExecution(
      senderAddress: Address,
      stx: SignedTransaction,
      world: WorldStateProxy[F]
  ): F[WorldStateProxy[F]] =
    world.getAccount(senderAddress).map { account =>
      world.putAccount(senderAddress, account.increaseBalance(-calculateUpfrontGas(stx)).increaseNonce())
    }

  private def prepareProgramContext(
      stx: SignedTransaction,
      blockHeader: BlockHeader,
      world: WorldStateProxy[F],
      config: EvmConfig
  ): F[ProgramContext[F]] = {
    val senderAddress = getSenderAddress(stx, blockHeader.number)
    if (stx.isContractInit) {
      for {
        address  <- world.createAddress(senderAddress)
        conflict <- world.nonEmptyCodeOrNonceAccount(address)
        code = if (conflict) ByteVector(INVALID.code) else stx.payload
        world1 <- world
          .initialiseAccount(address)
          .flatMap(_.transfer(senderAddress, address, UInt256(stx.value)))
      } yield ProgramContext(stx, address, Program(code), blockHeader, world1, config)
    } else {
      for {
        world1 <- world.transfer(senderAddress, stx.receivingAddress, UInt256(stx.value))
        code   <- world1.getCode(stx.receivingAddress)
      } yield ProgramContext(stx, stx.receivingAddress, Program(code), blockHeader, world1, config)
    }
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

    if (maxCodeSizeExceeded || (codeStoreOutOfGas && config.exceptionalFailedCodeDeposit)) {
      // Code size too big or code storage causes out-of-gas with exceptionalFailedCodeDeposit enabled
      result.copy(error = Some(OutOfGas))
    } else if (codeStoreOutOfGas && !config.exceptionalFailedCodeDeposit) {
      // Code storage causes out-of-gas with exceptionalFailedCodeDeposit disabled
      result
    } else {
      // Code storage succeeded
      result.copy(gasRemaining = result.gasRemaining - codeDepositCost,
                  world = result.world.putCode(address, result.returnData))
    }
  }

  private def pay(address: Address, value: UInt256)(world: WorldStateProxy[F]): F[WorldStateProxy[F]] =
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
    if (result.error.isDefined) {
      0
    } else {
      val gasUsed = stx.gasLimit - result.gasRemaining
      // remaining gas plus some allowance
      result.gasRemaining + (gasUsed / 2).min(result.gasRefund)
    }

  private def deleteAccounts(addressesToDelete: Set[Address])(
      worldStateProxy: WorldStateProxy[F]): F[WorldStateProxy[F]] =
    addressesToDelete.foldLeft(worldStateProxy) { case (world, address) => world.delAccount(address) }.pure[F]

  private def deleteEmptyTouchedAccounts(world: WorldStateProxy[F]): F[WorldStateProxy[F]] = {
    def deleteEmptyAccount(world: WorldStateProxy[F], address: Address): F[WorldStateProxy[F]] =
      Sync[F].ifM(world.getAccountOpt(address).exists(_.isEmpty(config.accountStartNonce)))(
        ifTrue = world.delAccount(address).pure[F],
        ifFalse = world.pure[F]
      )

    Foldable[List]
      .foldLeftM(world.touchedAccounts.toList, world)((world, address) => deleteEmptyAccount(world, address))
      .map(_.clearTouchedAccounts)
  }
}

object BlockExecutor {
  def apply[F[_]: Sync](
      config: BlockChainConfig,
      history: History[F],
      blockPool: BlockPool[F],
      consensus: Consensus[F]
  ): BlockExecutor[F] =
    new BlockExecutor[F](
      config,
      history,
      blockPool,
      consensus,
      new CommonHeaderValidator[F](history),
      new BlockValidator[F](),
      new TransactionValidator[F](config),
      new VM
    )
}
