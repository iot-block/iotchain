package jbok.core.ledger

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.models._
import jbok.core.validators.Validators
import jbok.crypto.signature.SecP256k1
import jbok.evm.{VM, WorldStateProxy}
import scodec.bits._

trait LedgerFixture extends BlockPoolFixture {
  val originKeyPair = SecP256k1.generateKeyPair[IO].unsafeRunSync()
  val receiverKeyPair = SecP256k1.generateKeyPair[IO].unsafeRunSync()
  val originAddress = Address(originKeyPair)
  val receiverAddress = Address(receiverKeyPair)
  val minerAddress = Address(666)

  val defaultBlockHeader = BlockHeader(
    parentHash = ByteVector.empty,
    ommersHash = ByteVector.empty,
    beneficiary = ByteVector.empty,
    stateRoot = ByteVector.empty,
    transactionsRoot = ByteVector.empty,
    receiptsRoot = ByteVector.empty,
    logsBloom = ByteVector.empty,
    difficulty = 1000000,
    number = blockChainConfig.homesteadBlockNumber + 1,
    gasLimit = 1000000,
    gasUsed = 0,
    unixTimestamp = 1486752441,
    extraData = ByteVector.empty,
    mixHash = ByteVector.empty,
    nonce = ByteVector.empty
  )

  val defaultTx = Transaction(
    nonce = 42,
    gasPrice = 1,
    gasLimit = 90000,
    receivingAddress = Some(receiverAddress),
    value = 0,
    payload = ByteVector.empty
  )

  val defaultLog = TxLogEntry(
    loggerAddress = originAddress,
    logTopics = List(hex"962cd36cf694aa154c5d3a551f19c98f356d906e96828eeb616e16fae6415738"),
    data = ByteVector.fromValidHex("1" * 128)
  )

  val initialOriginBalance: UInt256 = 100000000
  val initialMinerBalance: UInt256 = 2000000

  val initialOriginNonce = defaultTx.nonce

  val defaultAddressesToDelete = Set(Address(hex"01"), Address(hex"02"), Address(hex"03"))
  val defaultLogs = List(defaultLog.copy(loggerAddress = defaultAddressesToDelete.head))
  val defaultGasPrice: UInt256 = 10
  val defaultGasLimit: UInt256 = 1000000
  val defaultValue: BigInt = 1000

  val emptyWorld = blockChain.getWorldStateProxy(-1, UInt256.Zero, None).unsafeRunSync()

  val worldWithMinerAndOriginAccounts = WorldStateProxy
    .persist[IO](
      emptyWorld
        .saveAccount(originAddress, Account(nonce = UInt256(initialOriginNonce), balance = initialOriginBalance))
        .saveAccount(receiverAddress, Account(nonce = UInt256(initialOriginNonce), balance = initialOriginBalance))
        .saveAccount(minerAddress, Account(balance = initialMinerBalance))
    )
    .unsafeRunSync()

  val initialWorld = WorldStateProxy
    .persist[IO](
      defaultAddressesToDelete.foldLeft(worldWithMinerAndOriginAccounts) { (recWorld, address) =>
        recWorld.saveAccount(address, Account.empty())
      }
    )
    .unsafeRunSync()

  val validBlockParentHeader: BlockHeader = defaultBlockHeader.copy(
    stateRoot = initialWorld.stateRootHash
  )
  val validBlockHeader: BlockHeader = defaultBlockHeader.copy(
    stateRoot = initialWorld.stateRootHash,
    parentHash = validBlockParentHeader.hash,
    beneficiary = minerAddress.bytes,
    receiptsRoot = Account.EmptyStorageRootHash,
    logsBloom = BloomFilter.EmptyBloomFilter,
    gasLimit = defaultGasLimit,
    gasUsed = 0
  )
  val validBlockBodyWithNoTxs: BlockBody = BlockBody(Nil, Nil)
  val validTx: Transaction = defaultTx.copy(
    nonce = initialOriginNonce,
    gasLimit = defaultGasLimit,
    value = defaultValue
  )
  val validStxSignedByOrigin: SignedTransaction =
    SignedTransaction.sign(validTx, originKeyPair, Some(blockChainConfig.chainId))

//  blockchain.save(validBlockParentHeader)
//  blockchain.save(validBlockParentHeader.hash, validBlockBodyWithNoTxs)
//  storagesInstance.storages.appStateStorage.putBestBlockNumber(validBlockParentHeader.number)
//  storagesInstance.storages.totalDifficultyStorage.put(validBlockParentHeader.hash, 0)

  val ledger = Ledger[IO](new VM, blockChain, blockChainConfig, Validators(blockChain), blockPool)
//  sealed trait Changes
//  case class UpdateBalance(amount: UInt256) extends Changes
//  case object IncreaseNonce extends Changes
//  case object DeleteAccount extends Changes
//
//  def applyChanges(stateRootHash: ByteVector, blockchainStorages: BlockchainStorages, changes: Seq[(Address, Changes)]): ByteString = {
//    val initialWorld = BlockchainImpl(blockchainStorages).getWorldStateProxy(-1, UInt256.Zero, Some(stateRootHash))
//    val newWorld = changes.foldLeft[InMemoryWorldStateProxy](initialWorld){ case (recWorld, (address, change)) =>
//      change match {
//        case UpdateBalance(balanceIncrease) =>
//          val accountWithBalanceIncrease = recWorld.getAccount(address).getOrElse(Account.empty()).increaseBalance(balanceIncrease)
//          recWorld.saveAccount(address, accountWithBalanceIncrease)
//        case IncreaseNonce =>
//          val accountWithNonceIncrease = recWorld.getAccount(address).getOrElse(Account.empty()).increaseNonce()
//          recWorld.saveAccount(address, accountWithNonceIncrease)
//        case DeleteAccount =>
//          recWorld.deleteAccount(address)
//      }
//    }
//    InMemoryWorldStateProxy.persistState(newWorld).stateRootHash
//  }
}

class LedgerSpec extends JbokSpec {
  "ledger" should {
    "correctly calculate the total gas refund to be returned to the sender and paying for gas to the miner" in new LedgerFixture {
      val tx = defaultTx.copy(gasPrice = defaultGasPrice, gasLimit = defaultGasLimit)
      val stx = SignedTransaction.sign(tx, originKeyPair, Some(blockChainConfig.chainId))
      val header = defaultBlockHeader.copy(beneficiary = minerAddress.bytes)
      val execResult = ledger.executeTransaction(stx, header, worldWithMinerAndOriginAccounts).unsafeRunSync()
      val postTxWorld = execResult.world

      val balanceDelta = UInt256(execResult.gasUsed * defaultGasPrice)
      postTxWorld.getBalance(originAddress).unsafeRunSync() shouldBe (initialOriginBalance - balanceDelta)
      postTxWorld.getBalance(minerAddress).unsafeRunSync() shouldBe (initialMinerBalance + balanceDelta)
    }

    "executeBlockTransactions for a block without txs" in new LedgerFixture {
      val block = Block(validBlockHeader, validBlockBodyWithNoTxs)
      val txsExecResult = ledger.executeBlockTransactions(block).value.unsafeRunSync()
      txsExecResult.isRight shouldBe true
      val BlockResult(resultingWorldState, resultingGasUsed, resultingReceipts) = txsExecResult.right.get
      resultingGasUsed shouldBe 0
      resultingReceipts shouldBe Nil
      WorldStateProxy.persist[IO](resultingWorldState).unsafeRunSync().stateRootHash shouldBe validBlockHeader.stateRoot
    }

    "run a block with more than one tx" ignore new LedgerFixture {
      val table = Table[Address, Address, Address, Address](
        ("origin1Address", "receiver1Address", "origin2Address", "receiver2Address"),
        (originAddress, minerAddress, receiverAddress, minerAddress),
        (originAddress, receiverAddress, receiverAddress, originAddress),
        (originAddress, receiverAddress, originAddress, minerAddress),
        (originAddress, originAddress, originAddress, originAddress)
      )

      forAll(table) { (origin1Address, receiver1Address, origin2Address, receiver2Address) =>
        def keyPair(address: Address) = if (address == originAddress) originKeyPair else receiverKeyPair

        val tx1 = validTx.copy(value = 100, receivingAddress = Some(receiver1Address), gasLimit = defaultGasLimit)
        val tx2 = validTx.copy(
          value = 50,
          receivingAddress = Some(receiver2Address),
          gasLimit = defaultGasLimit * 2,
          nonce = validTx.nonce + (if (origin1Address == origin2Address) 1 else 0)
        )
        val stx1 = SignedTransaction.sign(tx1, keyPair(origin1Address), Some(blockChainConfig.chainId))
        val stx2 = SignedTransaction.sign(tx2, keyPair(origin2Address), Some(blockChainConfig.chainId))

        val validBlockBodyWithTxs: BlockBody = validBlockBodyWithNoTxs.copy(transactionList = List(stx1, stx2))
        val block = Block(validBlockHeader, validBlockBodyWithTxs)

        val txsExecResult = ledger.executeBlockTransactions(block).value.unsafeRunSync()

        txsExecResult.isRight shouldBe true
        val BlockResult(resultingWorldState, resultingGasUsed, resultingReceipts) = txsExecResult.right.get

        //Check valid gasUsed
        resultingGasUsed shouldBe stx1.tx.gasLimit + stx2.tx.gasLimit

        //Check valid receipts
        resultingReceipts.size shouldBe 2
        val Seq(receipt1, receipt2) = resultingReceipts

//        //Check receipt1
//        val minerPaymentForTx1 = UInt256(stx1.tx.gasLimit * stx1.tx.gasPrice)
//        val changesTx1 = Seq(
//          origin1Address -> IncreaseNonce,
//          origin1Address -> UpdateBalance(-minerPaymentForTx1),     //Origin payment for tx execution and nonce increase
//          minerAddress -> UpdateBalance(minerPaymentForTx1),        //Miner reward for tx execution
//          origin1Address -> UpdateBalance(-UInt256(stx1.tx.value)), //Discount tx.value from originAddress
//          receiver1Address -> UpdateBalance(UInt256(stx1.tx.value)) //Increase tx.value to recevierAddress
//        )
//        val expectedStateRootTx1 = applyChanges(validBlockParentHeader.stateRoot, blockchainStorages, changesTx1)
//
//        val Receipt(rootHashReceipt1, gasUsedReceipt1, logsBloomFilterReceipt1, logsReceipt1) = receipt1
//        rootHashReceipt1 shouldBe expectedStateRootTx1
//        gasUsedReceipt1 shouldBe stx1.tx.gasLimit
//        logsBloomFilterReceipt1 shouldBe BloomFilter.create(Nil)
//        logsReceipt1 shouldBe Nil
//
//        //Check receipt2
//        val minerPaymentForTx2 = UInt256(stx2.tx.gasLimit * stx2.tx.gasPrice)
//        val changesTx2 = Seq(
//          origin2Address -> IncreaseNonce,
//          origin2Address -> UpdateBalance(-minerPaymentForTx2),     //Origin payment for tx execution and nonce increase
//          minerAddress -> UpdateBalance(minerPaymentForTx2),        //Miner reward for tx execution
//          origin2Address -> UpdateBalance(-UInt256(stx2.tx.value)), //Discount tx.value from originAddress
//          receiver2Address -> UpdateBalance(UInt256(stx2.tx.value)) //Increase tx.value to recevierAddress
//        )
//        val expectedStateRootTx2 = applyChanges(expectedStateRootTx1, blockchainStorages, changesTx2)
//
//        val Receipt(rootHashReceipt2, gasUsedReceipt2, logsBloomFilterReceipt2, logsReceipt2) = receipt2
//        rootHashReceipt2 shouldBe expectedStateRootTx2
//        gasUsedReceipt2 shouldBe (stx1.tx.gasLimit + stx2.tx.gasLimit)
//        logsBloomFilterReceipt2 shouldBe BloomFilter.create(Nil)
//        logsReceipt2 shouldBe Nil
//
//        //Check world
//        InMemoryWorldStateProxy.persistState(resultingWorldState).stateRootHash shouldBe expectedStateRootTx2
//
//        val blockReward = ledger.blockRewardCalculator.calcBlockMinerReward(block.header.number, 0)
//        val changes = Seq(
//          minerAddress -> UpdateBalance(UInt256(blockReward))
//        )
//        val blockExpectedStateRoot = applyChanges(expectedStateRootTx2, blockchainStorages, changes)
//
//        val blockWithCorrectStateAndGasUsed = block.copy(
//          header = block.header.copy(stateRoot = blockExpectedStateRoot, gasUsed = gasUsedReceipt2)
//        )
//        assert(ledger.executeBlock(blockWithCorrectStateAndGasUsed).isRight)
      }
    }

    "produce empty block if all txs fail" ignore new LedgerFixture {
      val newAccountKeyPair = SecP256k1.generateKeyPair[IO].unsafeRunSync()
      val newAccountAddress = Address(newAccountKeyPair)
      val tx1: Transaction = defaultTx.copy(gasPrice = 42, receivingAddress = Some(Address(42)))
      val tx2: Transaction = defaultTx.copy(gasPrice = 42, receivingAddress = Some(Address(42)))
      val stx1: SignedTransaction = SignedTransaction.sign(tx1, newAccountKeyPair, Some(blockChainConfig.chainId))
      val stx2: SignedTransaction = SignedTransaction.sign(tx2, newAccountKeyPair, Some(blockChainConfig.chainId))

      val result: (BlockResult[IO], List[SignedTransaction]) = ledger.executePreparedTransactions(
        List(stx1, stx2),
        initialWorld,
        defaultBlockHeader
      ).unsafeRunSync()

      result match { case (_, executedTxs) => executedTxs shouldBe List.empty }
    }

    "create sender account if it does not exists" in new LedgerFixture {
      val inputData = ByteVector("the payload".getBytes)
      val newAccountKeyPair = SecP256k1.generateKeyPair[IO].unsafeRunSync()
      val newAccountAddress = Address(newAccountKeyPair)

      val tx: Transaction = defaultTx.copy(gasPrice = 0, receivingAddress = None, payload = inputData)
      val stx: SignedTransaction = SignedTransaction.sign(tx, newAccountKeyPair, Some(blockChainConfig.chainId))

      val result: Either[BlockExecutionError.TxsExecutionError[IO], BlockResult[IO]] = ledger.executeTransactions(
        List(stx),
        initialWorld,
        defaultBlockHeader
      ).value.unsafeRunSync()

      result.isRight shouldBe true
      result.map(br => br.worldState.getAccount(newAccountAddress).unsafeRunSync()) shouldBe Right(Account(nonce = 1))
    }
  }
}
