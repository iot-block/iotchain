package jbok.core.mining

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.consensus.{Consensus, ConsensusFixture}
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.models.{Address, SignedTransaction, UInt256}

class BlockMinerSpec extends JbokSpec {
  def check(newConsensus: () => ConsensusFixture): Unit =
    "BlockMiner" should {

      "generate block with no transaction" in new BlockMinerFixture(new CliqueFixture {}) {
        val parent = miner.history.getBestBlock.unsafeRunSync()
        val block  = miner.generateBlock(parent).unsafeRunSync()
      }

      "generate block with transactions" in new BlockMinerFixture(newConsensus()) {
        val txs = txGen.nextTxs(1)
        val stx = txs.head
        val parent = miner.history.getBestBlock.unsafeRunSync()
        val block  = miner.generateBlock(parent, txs, Nil).unsafeRunSync()
      }

      "mine blocks" in new BlockMinerFixture(newConsensus()) {
        val txs = txGen.nextTxs(100)
        val stx = txs.head
        val parent = miner.history.getBestBlock.unsafeRunSync()
        val block  = miner.generateBlock(parent, txs, Nil).unsafeRunSync()
        val mined  = miner.mine(block).unsafeRunSync().get
        miner.submitNewBlock(mined).unsafeRunSync()
      }

      "calculate the value, gas and reward transfer" in new BlockMinerFixture(newConsensus()) {
        val txs = txGen.nextTxs(1)
        val stx = txs.head
        val sender = SignedTransaction.getSender(stx).get
        val parent = history.getBestBlock.unsafeRunSync()
        val header = consensus.prepareHeader(parent, Nil).unsafeRunSync()
        val world = history
          .getWorldStateProxy(header.number, UInt256.Zero, Some(parent.header.stateRoot))
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

      "change the nonce" in new BlockMinerFixture(newConsensus()) {
        val txs = txGen.nextTxs(1)
        val stx = txs.head
        val sender = SignedTransaction.getSender(stx).get
        val parent = miner.history.getBestBlock.unsafeRunSync()
        val header = miner.executor.consensus.prepareHeader(parent, Nil).unsafeRunSync()
        val world = miner.history
          .getWorldStateProxy(header.number, UInt256.Zero, Some(parent.header.stateRoot))
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

  check(() => new CliqueFixture {})
}
