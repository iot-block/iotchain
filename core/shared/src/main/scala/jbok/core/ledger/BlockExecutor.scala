package jbok.core.ledger

import cats.Foldable
import cats.effect.concurrent.Semaphore
import cats.effect.{Resource, Sync, Timer}
import cats.implicits._
import fs2._
import jbok.common.ByteUtils
import jbok.core.config.HistoryConfig
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock._
import jbok.core.messages.SignedTransactions
import jbok.core.models.UInt256._
import jbok.core.models._
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.core.peer.{Peer, PeerSelector}
import jbok.core.pool.{BlockPool, TxPool}
import jbok.core.queue.{Consumer, Producer}
import jbok.core.validators.{HeaderValidator, TxValidator}
import jbok.persistent.mpt.MerklePatriciaTrie
import jbok.evm._
import scodec.bits.ByteVector
import jbok.common.log.Logger
import jbok.common.math.N
import jbok.common.math.implicits._

import scala.concurrent.duration._

final class BlockExecutor[F[_]](
    config: HistoryConfig,
    history: History[F],
    consensus: Consensus[F],
    txValidator: TxValidator[F],
    txPool: TxPool[F],
    blockPool: BlockPool[F],
    semaphore: Semaphore[F],
    inbound: Consumer[F, Peer[F], Block],
    outbound: Producer[F, PeerSelector[F], Block]
)(implicit F: Sync[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  import BlockExecutor._

  def handleReceivedBlock(received: ReceivedBlock[F]): F[List[Block]] =
    for {
      result <- importBlock(received.block)
    } yield result

  def handleSyncBlocks(requested: SyncBlocks[F]): F[List[Block]] =
    requested.blocks.flatTraverse(importBlock)

  def handleMinedBlock(mined: MinedBlock): F[List[Block]] =
    for {
      blocks <- importBlock(mined.block)
      messages = blocks.map(mkBroadcast)
      _ <- messages.traverse { case (selector, block) => outbound.produce(selector, block) }
    } yield blocks

  def mkBroadcast(block: Block) =
    PeerSelector.withoutBlock(block).andThen(PeerSelector.randomSelectSqrt(4)) -> block

  def handlePendingBlock(pending: PendingBlock): F[ExecutedBlock[F]] =
    for {
      best             <- history.getBestBlock
      (result, txs)    <- executeTransactions(pending.block, shortCircuit = false)
      transactionsRoot <- MerklePatriciaTrie.calcMerkleRoot[F, SignedTransaction](txs)
      receiptsRoot     <- MerklePatriciaTrie.calcMerkleRoot[F, Receipt](result.receipts)
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
        result.receipts
      )
      persisted <- executed.world.persisted
      header2 = header.copy(stateRoot = persisted.stateRootHash)
      block2  = executed.block.copy(header = header2)
    } yield executed.copy(block = block2, world = persisted)

  def simulateTransaction(stx: SignedTransaction, senderAddress: Address, blockHeader: BlockHeader): F[TxExecResult[F]] = {
    val stateRoot = blockHeader.stateRoot
    val gasLimit  = stx.gasLimit
    val vmConfig  = EvmConfig.forBlock(blockHeader.number, config)
    for {
      world1 <- history.getWorldState(config.accountStartNonce, Some(stateRoot))
      (senderAccount, world2) <- world1
        .getAccountOpt(senderAddress)
        .map(a => (a, world1))
        .getOrElse((Account.empty(UInt256.zero), world1.putAccount(senderAddress, Account.empty(UInt256.zero))))
      world3  <- updateSenderAccountBeforeExecution(senderAddress, stx, world2)
      context <- prepareProgramContext(stx, senderAddress, blockHeader, world2, vmConfig)
      result  <- runVM(stx, context, vmConfig)
      totalGasToRefund = calcTotalGasToRefund(stx, result)
    } yield {
      TxExecResult(result.world, gasLimit - totalGasToRefund, result.logs, result.returnData, result.error, result.contractAddress)
    }
  }

  def binarySearchGasEstimation(stx: SignedTransaction, senderAddress: Address, blockHeader: BlockHeader): F[N] = {
    val lowLimit  = EvmConfig.forBlock(blockHeader.number, config).feeSchedule.G_transaction
    val highLimit = stx.gasLimit

    if (highLimit < lowLimit)
      F.pure(highLimit)
    else {
      binaryChop(lowLimit, highLimit)(gasLimit => simulateTransaction(stx.copy(gasLimit = gasLimit), senderAddress, blockHeader).map(_.vmError))
    }
  }

  val stream: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/BlockExecutor")) ++
      inbound.consume
        .evalMap { case (peer, block) => handleReceivedBlock(ReceivedBlock(block, peer)) }
        .map {
          case block :: _ =>
            Some(PeerSelector.withoutBlock(block).andThen(PeerSelector.randomSelectSqrt(4)) -> block)
          case _ =>
            None
        }
        .unNone
        .through(outbound.sink)

  ////////////////////////////////
  ////////////////////////////////

  private[jbok] def executeBlock(block: Block): F[ExecutedBlock[F]] =
    for {
      (result, _) <- executeTransactions(block, shortCircuit = true)
      executed = ExecutedBlock(block, result.world, result.gasUsed, result.receipts)
      persisted <- executed.world.persisted
      _ <- HeaderValidator.postExecValidate[F](
        executed.block.header,
        persisted.stateRootHash,
        executed.receipts,
        executed.gasUsed
      )
      _ <- history.putBlockAndReceipts(executed.block, executed.receipts)
    } yield executed

  private def importBlock(block: Block): F[List[Block]] = {
    val greenLight = Resource.make(semaphore.acquire)(_ => semaphore.release)

    greenLight.use { _ =>
      consensus.run(block).flatMap[List[Block]] {
        case Consensus.Forward(blocks) =>
          blocks.traverse(executeBlock).map(_.map(_.block)) <* updateTxs(Nil, blocks)

        case Consensus.Fork(oldBranch, newBranch) =>
          newBranch.traverse(executeBlock).map(_.map(_.block)) <* updateTxs(oldBranch, newBranch)

        case Consensus.Stash(block) =>
          blockPool.addBlock(block).as(Nil)

        case Consensus.Discard(e) =>
          log.d(s"discard ${block.tag} because ${e}").as(Nil)
      }
    }
  }

  private def executeTransactions(block: Block, shortCircuit: Boolean): F[(BlockExecResult[F], List[SignedTransaction])] =
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
      _      <- log.debug(s"execute ${block.body.transactionList.length} transactions in ${end - start}ms")
    } yield result

  private[jbok] def executeTransactions(
      transactions: List[SignedTransaction],
      header: BlockHeader,
      world: WorldState[F],
      shortCircuit: Boolean,
      accGas: N = 0,
      accReceipts: List[Receipt] = Nil,
      accExecuted: List[SignedTransaction] = Nil
  ): F[(BlockExecResult[F], List[SignedTransaction])] = transactions match {
    case Nil =>
      F.pure((BlockExecResult(world = world, gasUsed = accGas, receipts = accReceipts), accExecuted))

    case stx :: tail =>
      for {
        result <- executeTransaction(stx, header, world, accGas).attempt.flatMap {
          case Left(e) =>
            log.warn(s"execute ${stx} error, ${e.getMessage}") >>
              (if (shortCircuit) {
                 F.raiseError[(BlockExecResult[F], List[SignedTransaction])](e)
               } else {
                 txPool.removeTransactions(stx :: Nil) >>
                   executeTransactions(
                     tail,
                     header,
                     world,
                     shortCircuit,
                     accGas,
                     accReceipts,
                     accExecuted
                   )
               })

          case Right(txResult) =>
            val stateRootHash = txResult.world.stateRootHash
            val receipt = Receipt(
              postTransactionStateHash = stateRootHash,
              cumulativeGasUsed = accGas + txResult.gasUsed,
              logsBloomFilter = BloomFilter.create(txResult.logs),
              logs = txResult.logs,
              txHash = stx.hash,
              gasUsed = txResult.gasUsed,
              contractAddress = txResult.contractAddress
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
      accGas: N
  ): F[TxExecResult[F]] =
    for {
      senderAddress <- stx.getSenderOrThrow[F]
      gasPrice = UInt256(stx.gasPrice)
      gasLimit = stx.gasLimit
      vmConfig = EvmConfig.forBlock(header.number, config)
      (senderAccount, worldForTx) <- world
        .getAccountOpt(senderAddress)
        .map(a => (a, world))
        .getOrElse((Account.empty(UInt256.zero), world.putAccount(senderAddress, Account.empty(UInt256.zero))))
      upfrontCost = calculateUpfrontCost(stx)
      _                    <- txValidator.validate(stx, senderAccount, header, upfrontCost, accGas)
      checkpointWorldState <- updateSenderAccountBeforeExecution(senderAddress, stx, worldForTx)
      context              <- prepareProgramContext(stx, senderAddress, header, checkpointWorldState, vmConfig)
      result               <- runVM(stx, context, vmConfig)
      resultWithErrorHandling = if (result.error.isDefined || result.isRevert) {
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
      TxExecResult(world2, executionGasToPayToMiner, resultWithErrorHandling.logs, result.returnData, result.error, result.contractAddress)
    }

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
        address  <- world.createContractAddress(sender)
        conflict <- world.nonEmptyCodeOrNonce(address)
        context <- if (conflict) {
          F.pure(ProgramContext(stx, sender, address, Program(ByteVector(INVALID.code)), blockHeader, world, config))
        } else {
          world
            .initialiseAccount(address)
            .flatMap(_.transfer(sender, address, UInt256(stx.value)))
            .map(world => ProgramContext(stx, sender, address, Program(stx.payload), blockHeader, world, config))
        }
      } yield context
    } else {
      for {
        transferred <- world.transfer(sender, stx.receivingAddress, UInt256(stx.value))
        code        <- transferred.getCode(stx.receivingAddress)
      } yield ProgramContext(stx, sender, stx.receivingAddress, Program(code), blockHeader, transferred, config)
    }

  private def runVM(stx: SignedTransaction, context: ProgramContext[F], config: EvmConfig): F[ProgramResult[F]] =
    for {
      result <- VM.run(context)
    } yield {
      if (stx.isContractInit && result.error.isEmpty && !result.isRevert)
        saveNewContract(context.env.ownerAddr, result, config)
      else
        result
    }

  private def saveNewContract(address: Address, result: ProgramResult[F], config: EvmConfig): ProgramResult[F] = {
    val contractCode    = result.returnData
    val codeDepositCost = config.calcCodeDepositCost(contractCode)

    val maxCodeSizeExceeded = config.maxCodeSize.exists(codeSizeLimit => contractCode.size > codeSizeLimit)
    val codeStoreOutOfGas   = result.gasRemaining < codeDepositCost

    if (maxCodeSizeExceeded || codeStoreOutOfGas) {
      // Code size too big or code storage causes out-of-gas with exceptionalFailedCodeDeposit enabled
      result.copy(error = Some(OutOfGas))
    } else {
      // Code storage succeeded
      result.copy(gasRemaining = result.gasRemaining - codeDepositCost, world = result.world.putCode(address, result.returnData), contractAddress = Some(address))
    }
  }

  private def pay(address: Address, value: UInt256)(world: WorldState[F]): F[WorldState[F]] =
    world
      .isZeroValueTransferToNonExistentAccount(address, value)
      .ifM(
        ifTrue = world.pure[F],
        ifFalse = for {
          account <- world.getAccountOpt(address).getOrElse(Account.empty(config.accountStartNonce))
        } yield world.putAccount(address, account.increaseBalance(value)).touchAccounts(address)
      )

  private def calculateUpfrontCost(stx: SignedTransaction): UInt256 =
    UInt256(calculateUpfrontGas(stx) + stx.value)

  private def calculateUpfrontGas(stx: SignedTransaction): UInt256 =
    UInt256(stx.gasLimit * stx.gasPrice)

  private def calcTotalGasToRefund(stx: SignedTransaction, result: ProgramResult[F]): N =
    if (result.error.isEmpty || result.isRevert) {
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

  private def binaryChop[E](min: N, max: N)(f: N => F[Option[E]]): F[N] = {
    assert(min <= max)
    if (min == max)
      F.pure(max)
    else {
      val mid           = min + (max - min) / 2
      val possibleError = f(mid)
      F.ifM(possibleError.map(_.isEmpty))(ifTrue = binaryChop(min, mid)(f), ifFalse = binaryChop(mid + 1, max)(f))
    }
  }

  private def updateTxs(blocksRemoved: List[Block], blocksAdded: List[Block]): F[Unit] =
    for {
      _ <- blocksRemoved.map(_.body.transactionList).traverse(txs => txPool.addTransactions(SignedTransactions(txs)))
      _ <- blocksAdded.traverse(block => txPool.removeTransactions(block.body.transactionList))
    } yield ()
}

object BlockExecutor {
  final case class BlockExecResult[F[_]](
      world: WorldState[F],
      gasUsed: N = 0,
      receipts: List[Receipt] = Nil
  )

  final case class TxExecResult[F[_]](
      world: WorldState[F],
      gasUsed: N,
      logs: List[TxLogEntry],
      vmReturnData: ByteVector,
      vmError: Option[ProgramError],
      contractAddress: Option[Address] = None
  )
}
