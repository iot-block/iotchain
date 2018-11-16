package jbok.core.mining

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.testkit._
import jbok.core.models.{Address, SignedTransaction, UInt256}
import jbok.core.testkit._

class BlockMinerSpec extends JbokSpec {
  implicit val fixture = defaultFixture()

  "BlockMiner" should {
    "generate block with no transaction" in {
      val miner  = random[BlockMiner[IO]]
      val parent = miner.history.getBestBlock.unsafeRunSync()
      val block  = miner.generateBlock(parent.some).unsafeRunSync()
    }

    "generate block with transactions" in {
      val miner = random[BlockMiner[IO]]
      val txs   = random[List[SignedTransaction]](genTxs(1, 1024))
      println(txs.length)
      val parent = miner.history.getBestBlock.unsafeRunSync()
      val block  = miner.generateBlock(parent.some, txs.some).unsafeRunSync()
    }

    "mine blocks" in {
      val N     = 10
      val miner = random[BlockMiner[IO]]
      miner.miningStream().take(N).compile.toList.unsafeRunSync()
      miner.history.getBestBlockNumber.unsafeRunSync() shouldBe N
    }

    "calculate the value, gas and reward transfer" in {
      val miner  = random[BlockMiner[IO]]
      val txs    = random[List[SignedTransaction]](genTxs(1, 1))
      val stx    = txs.head
      val sender = SignedTransaction.getSender(stx).get
      val parent = miner.history.getBestBlock.unsafeRunSync()
      val header = miner.consensus.prepareHeader(parent, Nil).unsafeRunSync()
      val world = miner.history
        .getWorldState(UInt256.Zero, Some(parent.header.stateRoot))
        .unsafeRunSync()
      val initSenderBalance =
        world.getBalance(sender).unsafeRunSync()
      val initMinerBalance =
        world.getBalance(Address(header.beneficiary)).unsafeRunSync()
      val initReceiverBalance =
        world.getBalance(stx.receivingAddress).unsafeRunSync()

      val result = miner.executor.executeTransaction(stx, header, world).unsafeRunSync()
      val gas    = UInt256(result.gasUsed * stx.gasPrice)
      result.world.getBalance(sender).unsafeRunSync() shouldBe (initSenderBalance - gas - stx.value)
      result.world.getBalance(Address(header.beneficiary)).unsafeRunSync() shouldBe (initMinerBalance + gas)
      result.world.getBalance(stx.receivingAddress).unsafeRunSync() shouldBe (initReceiverBalance + stx.value)
    }

    "change the nonce" in {
      val miner  = random[BlockMiner[IO]]
      val txs    = random[List[SignedTransaction]](genTxs(1, 1))
      val stx    = txs.head
      val sender = SignedTransaction.getSender(stx).get
      val parent = miner.history.getBestBlock.unsafeRunSync()
      val header = miner.executor.consensus.prepareHeader(parent, Nil).unsafeRunSync()
      val world = miner.history
        .getWorldState(UInt256.Zero, Some(parent.header.stateRoot))
        .unsafeRunSync()
      val preNonce  = world.getAccount(sender).unsafeRunSync().nonce
      val result    = miner.executor.executeTransaction(stx, header, world).unsafeRunSync()
      val postNonce = result.world.getAccount(SignedTransaction.getSender(stx).get).unsafeRunSync().nonce

      postNonce shouldBe preNonce + 1
    }

    "correctly run executeBlockTransactions for a block without txs" ignore {}

    "correctly run executeBlockTransactions for a block with one tx (that produces no errors)" ignore {}

    "correctly run executeBlockTransactions for a block with one tx (that produces OutOfGas)" ignore {}

    "correctly run executeBlock for a valid block without txs" ignore {}

    "fail to run executeBlock if a block is invalid before executing it" ignore {}

    "fail to run executeBlock if a block is invalid after executing it" ignore {}

    "correctly run a block with more than one tx" ignore {}

    "run out of gas in contract creation after Homestead" ignore {}

    "clear logs only if vm execution results in an error" ignore {}

    "correctly send the transaction input data whether it's a contract creation or not" ignore {}

    "should handle pre-existing and new destination accounts when processing a contract init transaction" ignore {}

    "create sender account if it does not exists" ignore {}

    "remember executed transaction in case of many failures in the middle" ignore {}

    "produce empty block if all txs fail" ignore {}
  }
}
