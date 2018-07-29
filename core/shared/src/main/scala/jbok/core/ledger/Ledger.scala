package jbok.core.ledger

import cats.Foldable
import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect.Effect
import cats.implicits._
import jbok.common._
import jbok.core.BlockChain
import jbok.core.configs.{BlockChainConfig, MonetaryPolicyConfig}
import jbok.core.ledger.BlockExecutionError.TxsExecutionError
import jbok.core.ledger.BlockImportResult.{BlockImportFailed, BlockImported}
import jbok.core.ledger.BlockPool.Leaf
import jbok.core.ledger.io.iohk.ethereum.ledger.BlockRewardCalculator
import jbok.core.mining.BlockPreparationResult
import jbok.core.models.UInt256._
import jbok.core.models._
import jbok.core.validators.{Invalid, Validators}
import jbok.evm._
import scodec.bits.ByteVector

sealed trait BlockStatus
case object InChain extends BlockStatus
case object Pooled extends BlockStatus
case object UnknownBlock extends BlockStatus

sealed trait BlockImportResult
object BlockImportResult {
  case class BlockImported(imported: List[Block], totalDifficulties: List[BigInt]) extends BlockImportResult
  case class BlockImportFailed(error: String) extends BlockImportResult
  case object DuplicateBlock extends BlockImportResult
  case object UnknownParent extends BlockImportResult
  case object BlockPooled extends BlockImportResult
}

sealed trait BlockExecutionError {
  val reason: Any
}

object BlockExecutionError {
  case class ValidationBeforeExecError(reason: Any) extends BlockExecutionError
  case class StateBeforeFailure[F[_]](worldState: WorldStateProxy[F], acumGas: BigInt, acumReceipts: List[Receipt])
  case class TxsExecutionError[F[_]](stx: SignedTransaction, stateBeforeError: StateBeforeFailure[F], reason: String)
      extends BlockExecutionError
  case class ValidationAfterExecError(reason: String) extends BlockExecutionError
}

case class BlockResult[F[_]](worldState: WorldStateProxy[F], gasUsed: BigInt = 0, receipts: List[Receipt] = Nil)
case class TxResult[F[_]](
    world: WorldStateProxy[F],
    gasUsed: BigInt,
    logs: List[TxLogEntry],
    vmReturnData: ByteVector,
    vmError: Option[ProgramError]
)

case class Ledger[F[_]](
    vm: VM,
    blockChain: BlockChain[F],
    blockChainConfig: BlockChainConfig,
    validators: Validators[F],
    blockPool: BlockPool[F],
    blockRewardCalculator: BlockRewardCalculator = new BlockRewardCalculator(MonetaryPolicyConfig())
)(implicit F: Effect[F]) {
  protected[this] val log = org.log4s.getLogger

  def simulateTransaction(stx: SignedTransaction, blockHeader: BlockHeader): F[TxResult[F]] = ???

  def binarySearchGasEstimation(stx: SignedTransaction, blockHeader: BlockHeader): F[BigInt] = ???

  def importBlock(block: Block): F[BlockImportResult] =
    validateBlockBefore(block).value.flatMap {
      case Left(invalid) =>
        log.info(s"import block: failed")
        F.pure(BlockImportResult.BlockImportFailed(invalid.toString))

      case Right(validated) =>
        F.ifM(isDuplicate(validated.header.hash))(
          ifTrue = {
            log.info(s"import block: duplicate")
            F.pure(BlockImportResult.DuplicateBlock)
          },
          ifFalse = for {
            bestBlock <- blockChain.getBestBlock
            currentTd <- blockChain.getTotalDifficultyByHash(bestBlock.header.hash).map(_.get)
            isTopOfChain = block.header.parentHash == bestBlock.header.hash
            result <- if (isTopOfChain) {
              log.info(s"import block: about to import to top")
              importBlockToTop(block, bestBlock.header.number, currentTd)
            } else {
              log.info(s"import block: pooled")
              poolBlock(block, bestBlock, currentTd)
            }
          } yield result
        )
    }

  def isDuplicate(blockHash: ByteVector): F[Boolean] =
    blockChain.getBlockByHash(blockHash).map(_.isDefined) || blockPool.getBlockByHash(blockHash).map(_.isDefined)

  def importBlockToTop(block: Block, bestBlockNumber: BigInt, currentTd: BigInt): F[BlockImportResult] =
    for {
      topBlockHash <- blockPool.addBlock(block, bestBlockNumber).map(_.get.hash)
      _ = log.info(s"topBlockHash: ${topBlockHash}")
      topBlocks <- blockPool.getBranch(topBlockHash, dequeue = true)
      _ = log.info(s"topBlocks: ${topBlocks}")
      (importedBlocks, maybeError) <- executeBlocks(topBlocks, currentTd)
      _ = log.info(s"importedBlocks: ${importedBlocks}")
      totalDifficulties = importedBlocks
        .foldLeft(List(currentTd)) { (tds, b) =>
          (tds.head + b.header.difficulty) :: tds
        }
        .reverse
        .tail


      result <- maybeError match {
        case None =>
          log.info(s"block import succeed")
          BlockImported(importedBlocks, totalDifficulties).pure[F]

        case Some(error) if importedBlocks.isEmpty =>
          log.info(s"block import failed because ${error}")
          blockPool.removeSubtree(block.header.hash).map(_ => BlockImportFailed(error.toString))

        case Some(error) =>
          log.info(s"block import succeed")
          val p = topBlocks.drop(importedBlocks.length).headOption match {
            case Some(failedBlock) => blockPool.removeSubtree(failedBlock.header.hash)
            case None              => F.unit
          }
          p.map(_ => BlockImported(importedBlocks, totalDifficulties))
      }

    } yield result

  def poolBlock(block: Block, bestBlock: Block, currentTd: BigInt): F[BlockImportResult] =
    blockPool.addBlock(block, bestBlock.header.number).map(_ => BlockImportResult.BlockPooled)

  def validateBlockBefore(block: Block): EitherT[F, Invalid, Block] = {
    val et = for {
      block1 <- validators.blockHeaderValidator.validate(block)
      block2 <- validators.blockValidator.validateHeaderAndBody(block1)
      _ <- validators.ommersValidator.validate(
        block2.header.parentHash,
        block2.header.number,
        block2.body.uncleNodesList,
        getHeaderFromChainOrQueue,
        getNBlocksBackFromChainOrQueue
      )
    } yield block2

    et.leftMap(_.asInstanceOf[Invalid])
  }

  def validateBlockAfter(
      block: Block,
      stateRootHash: ByteVector,
      receipts: List[Receipt],
      gasUsed: BigInt
  ): Either[String, Unit] = {
    lazy val blockAndReceiptsValidation = validators.blockValidator.validateBlockAndReceipts(block.header, receipts)
    if (block.header.gasUsed != gasUsed) {
      Left(s"Block has invalid gas used, expected ${block.header.gasUsed} but got $gasUsed")
    } else if (block.header.stateRoot != stateRootHash) {
      Left(
        s"Block has invalid state root hash, expected ${block.header.stateRoot.toHex} but got ${stateRootHash.toHex}")
    } else {
      Right(())
    }
  }

  def executeTransactions(
      stxs: List[SignedTransaction],
      world: WorldStateProxy[F],
      blockHeader: BlockHeader,
      acumGas: BigInt = 0,
      acumReceipts: List[Receipt] = Nil
  ): EitherT[F, TxsExecutionError[F], BlockResult[F]] = stxs match {
    case Nil =>
      EitherT.rightT(BlockResult(worldState = world, gasUsed = acumGas, receipts = acumReceipts))

    case stx :: otherStxs =>
      for {
        (senderAccount, worldForTx) <- EitherT
          .right(
            world
              .getAccountOpt(stx.senderAddress)
              .map(a => (a, world))
              .getOrElse(
                (Account.empty(UInt256.Zero), world.saveAccount(stx.senderAddress, Account.empty(UInt256.Zero)))))
        upfrontCost = calculateUpfrontCost(stx.tx)
//        validated <- validators.transactionValidator.validate(stx, senderAccount, blockHeader, upfrontCost, acumGas)
        txResult <- EitherT.right(executeTransaction(stx, blockHeader, worldForTx))
        receipt = Receipt(
          postTransactionStateHash = txResult.world.stateRootHash,
          cumulativeGasUsed = acumGas + txResult.gasUsed,
          logsBloomFilter = BloomFilter.create(txResult.logs),
          logs = txResult.logs
        )
        result <- executeTransactions(
          otherStxs,
          txResult.world,
          blockHeader,
          receipt.cumulativeGasUsed,
          acumReceipts :+ receipt
        )
      } yield result
  }

  def executePreparedTransactions(
      signedTransactions: List[SignedTransaction],
      world: WorldStateProxy[F],
      blockHeader: BlockHeader,
      acumGas: BigInt = 0,
      acumReceipts: List[Receipt] = Nil,
      executed: List[SignedTransaction] = Nil
  ): F[(BlockResult[F], List[SignedTransaction])] =
    executeTransactions(signedTransactions, world, blockHeader, acumGas, acumReceipts).value.flatMap(
      _.fold(
        error => {
          val txIndex = signedTransactions.indexWhere(tx => tx.hash == error.stx.hash)
          executePreparedTransactions(
            signedTransactions.drop(txIndex + 1),
            error.stateBeforeError.worldState,
            blockHeader,
            error.stateBeforeError.acumGas,
            error.stateBeforeError.acumReceipts,
            executed ++ signedTransactions.take(txIndex)
          )
        }, { br =>
          (br -> (executed ++ signedTransactions)).pure[F]
        }
      ))

  def executeBlockTransactions(block: Block): EitherT[F, TxsExecutionError[F], BlockResult[F]] = {
    val world = for {
      parentStateRoot <- blockChain.getBlockHeaderByHash(block.header.parentHash).map(_.map(_.stateRoot))
      world <- blockChain.getWorldStateProxy(
        block.header.number,
        blockChainConfig.accountStartNonce,
        parentStateRoot,
        EvmConfig.forBlock(block.header.number, blockChainConfig).noEmptyAccounts
      )
    } yield world

    val blockTxsExecResult =
      EitherT.right(world).flatMap(world => executeTransactions(block.body.transactionList, world, block.header))
    blockTxsExecResult
  }

  def executeBlock(block: Block, alreadyValidated: Boolean = false): EitherT[F, BlockExecutionError, List[Receipt]] = {
    val execResult = for {
//      validated <- if (alreadyValidated) EitherT.fromEither[F](Right(block)) else validateBlockBefore(block)
      execResult <- executeBlockTransactions(block)
    } yield execResult

    execResult
      .leftMap(error => {
        log.info(s"execute ${block.id} failed because ${error}")
        error.asInstanceOf[BlockExecutionError]
      })
      .semiflatMap {
        case BlockResult(resultingWorldStateProxy, gasUsed, receipts) =>
          log.info(s"execute ${block.id} succeed")
          for {
            worldToPersist <- payBlockReward(block, resultingWorldStateProxy)
            worldPersisted <- WorldStateProxy.persist(worldToPersist) // State root hash needs to be up-to-date for validateBlockAfterExecution
            after = validateBlockAfter(block, worldPersisted.stateRootHash, receipts, gasUsed)
          } yield receipts
      }
  }

  def executeBlocks(blocks: List[Block], parentTd: BigInt): F[(List[Block], Option[Invalid])] = {
    log.info(s"start executing ${blocks.length} blocks")
    blocks match {
      case block :: remainingBlocks =>
        executeBlock(block, alreadyValidated = true).value.flatMap {
          case Right(receipts) =>
            log.info(s"execute block ${block.header.hash.toHex.take(7)} succeed")
            val td = parentTd + block.header.difficulty
            for {
              _ <- blockChain.save(block, receipts, td, saveAsBestBlock = true)
              (executedBlocks, error) <- executeBlocks(remainingBlocks, td)
            } yield (block :: executedBlocks, error)

          case Left(error) =>
            log.info(s"execute block ${block.header.hash.toHex.take(7)} failed")
            F.pure(List.empty[Block] -> Some(error.asInstanceOf[Invalid]))
        }

      case Nil =>
        F.pure((Nil, None))
    }
  }

  def payBlockReward(block: Block, worldStateProxy: WorldStateProxy[F]): F[WorldStateProxy[F]] = {
    def getAccountToPay(address: Address, ws: WorldStateProxy[F]): F[Account] = ws.getAccountOpt(address)
      .getOrElse(Account.empty(blockChainConfig.accountStartNonce))

    val minerAddress = Address(block.header.beneficiary)

    for {
      minerAccount <- getAccountToPay(minerAddress, worldStateProxy)
      minerReward = blockRewardCalculator.calcBlockMinerReward(block.header.number, block.body.uncleNodesList.size)
      afterMinerReward = worldStateProxy.saveAccount(minerAddress, minerAccount.increaseBalance(UInt256(minerReward)))
      _ = log.info(s"Paying block ${block.header.number} reward of $minerReward to miner with account address $minerAddress")
      world <- Foldable[List].foldLeftM(block.body.uncleNodesList, afterMinerReward) { (ws, ommer) =>
        val ommerAddress = Address(ommer.beneficiary)
        for {
          account <- getAccountToPay(ommerAddress, ws)
          ommerReward = blockRewardCalculator.calcOmmerMinerReward(block.header.number, ommer.number)
          _ = log.info(s"Paying block ${block.header.number} reward of $ommerReward to ommer with account address $ommerAddress")
        } yield ws.saveAccount(ommerAddress, account.increaseBalance(UInt256(ommerReward)))
      }
    } yield world
  }

  def getHeaderFromChainOrQueue(hash: ByteVector): F[Option[BlockHeader]] =
    OptionT(blockChain.getBlockHeaderByHash(hash))
      .orElseF(blockPool.getBlockByHash(hash).map(_.map(_.header)))
      .value

  def getNBlocksBackFromChainOrQueue(hash: ByteVector, n: Int): F[List[Block]] = {
    for {
      pooledBlocks <- blockPool.getBranch(hash, dequeue = false).map(_.take(n))
      result <- if (pooledBlocks.length == n) {
        pooledBlocks.pure[F]
      } else {
        val chainedBlockHash = pooledBlocks.headOption.map(_.header.parentHash).getOrElse(hash)
        blockChain.getBlockByHash(chainedBlockHash).flatMap {
          case None =>
            F.pure(List.empty[Block])

          case Some(block) =>
            val remaining = n - pooledBlocks.length - 1
            val numbers = (block.header.number - remaining) until block.header.number
            numbers.toList.map(blockChain.getBlockByNumber).sequence.map(xs => (xs.flatten :+ block) ::: pooledBlocks)
        }
      }
    } yield result
  }

  def executeTransaction(
      stx: SignedTransaction,
      blockHeader: BlockHeader,
      worldProxy: WorldStateProxy[F]
  ): F[TxResult[F]] = {
    log.info(s"Transaction(${stx.hash.toHex.take(7)}) execution start")
    val gasPrice = UInt256(stx.tx.gasPrice)
    val gasLimit = stx.tx.gasLimit
    val config = EvmConfig.forBlock(blockHeader.number, blockChainConfig)

    for {
      checkpointWorldState <- updateSenderAccountBeforeExecution(stx, worldProxy)
      context <- prepareProgramContext(stx, blockHeader, checkpointWorldState, config)
      result <- runVM(stx, context, config)
      resultWithErrorHandling = if (result.error.isDefined) {
        //Rollback to the world before transfer was done if an error happened
        result.copy(world = checkpointWorldState, addressesToDelete = Set.empty, logs = Nil)
      } else {
        result
      }

      totalGasToRefund = calcTotalGasToRefund(stx, resultWithErrorHandling)
      executionGasToPayToMiner = gasLimit - totalGasToRefund
      refundGasFn = pay(stx.senderAddress, (totalGasToRefund * gasPrice).toUInt256) _
      payMinerForGasFn = pay(Address(blockHeader.beneficiary), (executionGasToPayToMiner * gasPrice).toUInt256) _
      deleteAccountsFn = deleteAccounts(resultWithErrorHandling.addressesToDelete) _
      deleteTouchedAccountsFn = deleteEmptyTouchedAccounts _
      persistStateFn = WorldStateProxy.persist[F] _
      worldAfterPayments <- Kleisli(refundGasFn).andThen(payMinerForGasFn).run(resultWithErrorHandling.world)
      world2 <- Kleisli(deleteAccountsFn)
        .andThen(deleteTouchedAccountsFn)
        .andThen(persistStateFn)
        .run(worldAfterPayments)

    } yield {
      log.info(
        s"""Transaction(${stx.hash.toHex.take(7)}) execution end. Summary:
                     | - Error: ${result.error}.
                     | - Total Gas to Refund: $totalGasToRefund
                     | - Execution gas paid to miner: $executionGasToPayToMiner""".stripMargin
      )

      TxResult(world2, executionGasToPayToMiner, resultWithErrorHandling.logs, result.returnData, result.error)
    }
  }

  def runVM(stx: SignedTransaction, context: ProgramContext[F], config: EvmConfig): F[ProgramResult[F]] =
    for {
      result <- vm.run(context)
    } yield {
      if (stx.tx.isContractInit && result.error.isEmpty)
        saveNewContract(context.env.ownerAddr, result, config)
      else
        result
    }

  def saveNewContract(address: Address, result: ProgramResult[F], config: EvmConfig): ProgramResult[F] = {
    val contractCode = result.returnData
    val codeDepositCost = config.calcCodeDepositCost(contractCode)

    val maxCodeSizeExceeded = blockChainConfig.maxCodeSize.exists(codeSizeLimit => contractCode.size > codeSizeLimit)
    val codeStoreOutOfGas = result.gasRemaining < codeDepositCost

    if (maxCodeSizeExceeded || (codeStoreOutOfGas && config.exceptionalFailedCodeDeposit)) {
      // Code size too big or code storage causes out-of-gas with exceptionalFailedCodeDeposit enabled
      result.copy(error = Some(OutOfGas))
    } else if (codeStoreOutOfGas && !config.exceptionalFailedCodeDeposit) {
      // Code storage causes out-of-gas with exceptionalFailedCodeDeposit disabled
      result
    } else {
      // Code storage succeeded
      result.copy(gasRemaining = result.gasRemaining - codeDepositCost,
                  world = result.world.saveCode(address, result.returnData))
    }
  }

  def updateSenderAccountBeforeExecution(stx: SignedTransaction, world: WorldStateProxy[F]): F[WorldStateProxy[F]] = {
    val senderAddress = stx.senderAddress
    world.getAccount(senderAddress).map { account =>
      world.saveAccount(senderAddress, account.increaseBalance(-calculateUpfrontGas(stx.tx)).increaseNonce())
    }
  }

  def prepareProgramContext(
      stx: SignedTransaction,
      blockHeader: BlockHeader,
      world: WorldStateProxy[F],
      config: EvmConfig
  ): F[ProgramContext[F]] =
    stx.tx.receivingAddress match {
      case None =>
        for {
          address <- world.createAddress(creatorAddr = stx.senderAddress)
          conflict <- world.nonEmptyCodeOrNonceAccount(address)
          code = if (conflict) ByteVector(INVALID.code) else stx.tx.payload
          world1 <- world
            .initialiseAccount(address)
            .flatMap(_.transfer(stx.senderAddress, address, UInt256(stx.tx.value)))
        } yield ProgramContext(stx, address, Program(code), blockHeader, world1, config)

      case Some(txReceivingAddress) =>
        for {
          world1 <- world.transfer(stx.senderAddress, txReceivingAddress, UInt256(stx.tx.value))
          code <- world1.getCode(txReceivingAddress)
        } yield ProgramContext(stx, txReceivingAddress, Program(code), blockHeader, world1, config)
    }

  def calculateUpfrontCost(tx: Transaction): UInt256 =
    UInt256(calculateUpfrontGas(tx) + tx.value)

  def calculateUpfrontGas(tx: Transaction): UInt256 =
    UInt256(tx.gasLimit * tx.gasPrice)

  // YP 6.2
  def calcTotalGasToRefund(stx: SignedTransaction, result: ProgramResult[F]): BigInt =
    if (result.error.isDefined) {
      0
    } else {
      val gasUsed = stx.tx.gasLimit - result.gasRemaining
      // remaining gas plus some allowance
      result.gasRemaining + (gasUsed / 2).min(result.gasRefund)
    }

  def deleteAccounts(addressesToDelete: Set[Address])(worldStateProxy: WorldStateProxy[F]): F[WorldStateProxy[F]] =
    addressesToDelete.foldLeft(worldStateProxy) { case (world, address) => world.deleteAccount(address) }.pure[F]

  def pay(address: Address, value: UInt256)(world: WorldStateProxy[F]): F[WorldStateProxy[F]] =
    F.ifM(world.isZeroValueTransferToNonExistentAccount(address, value))(
      ifTrue = world.pure[F],
      ifFalse = for {
        account <- world.getAccountOpt(address).getOrElse(Account.empty(blockChainConfig.accountStartNonce))
      } yield world.saveAccount(address, account.increaseBalance(value)).touchAccounts(address)
    )

  def deleteEmptyTouchedAccounts(world: WorldStateProxy[F]): F[WorldStateProxy[F]] = {
    def deleteEmptyAccount(world: WorldStateProxy[F], address: Address): F[WorldStateProxy[F]] =
      F.ifM(world.getAccountOpt(address).exists(_.isEmpty(blockChainConfig.accountStartNonce)))(
        ifTrue = world.deleteAccount(address).pure[F],
        ifFalse = world.pure[F]
      )

    Foldable[List]
      .foldLeftM(world.touchedAccounts.toList, world)((world, address) => deleteEmptyAccount(world, address))
      .map(_.clearTouchedAccounts)
  }

  def prepareBlock(block: Block): F[BlockPreparationResult[F]] =
    for {
      parentStateRoot <- blockChain.getBlockHeaderByHash(block.header.parentHash).map(_.map(_.stateRoot))
      initialWorld <- blockChain.getWorldStateProxy(0, blockChainConfig.accountStartNonce, parentStateRoot)
      (br, stxs) <- executePreparedTransactions(block.body.transactionList, initialWorld, block.header)
      worldToPersist <- payBlockReward(block, br.worldState)
      worldPersisted <- WorldStateProxy.persist(worldToPersist)
    } yield {
      BlockPreparationResult(
        block.copy(body = block.body.copy(transactionList = stxs)),
        br,
        worldPersisted.stateRootHash
      )
    }
}
