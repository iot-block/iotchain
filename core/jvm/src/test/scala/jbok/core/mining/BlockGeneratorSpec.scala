package jbok.core.mining

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.configs.{BlockChainConfig, MiningConfig, MonetaryPolicyConfig, Timeouts}
import jbok.core.ledger.LedgerFixture
import jbok.core.models.{Address, SignedTransaction, Transaction, UInt256}
import jbok.core.validators.Validators
import jbok.crypto.signature.{KeyPair, SecP256k1}
import scodec.bits._

trait BlockGeneratorFixture extends LedgerFixture {
  val testAddress = 42
  val privateKey =
    KeyPair.Secret(BigInt(1, hex"f3202185c84325302d43887e90a2e23e7bc058d0450bb58ef2f7585765d7d48b".toArray))
  val pubKey = SecP256k1.buildPublicKeyFromPrivate[IO](privateKey).unsafeRunSync()
  val keyPair = KeyPair(pubKey, privateKey)
  val address = Address(keyPair)

  val txGasLimit = 21000
  val txTransfer = 9000
  val transaction = Transaction(
    nonce = 0,
    gasPrice = 1,
    gasLimit = txGasLimit,
    receivingAddress = Address(testAddress),
    value = txTransfer,
    payload = ByteVector.empty
  )
  val signedTransaction: SignedTransaction = SignedTransaction.sign(transaction, keyPair, Some(0x3d.toByte))
  val duplicatedSignedTransaction: SignedTransaction =
    SignedTransaction.sign(transaction.copy(gasLimit = 2), keyPair, Some(0x3d.toByte))

  val timeouts = Timeouts()
  val miningConfig = MiningConfig()
  val blockTimestampProvider = new FakeBlockTimestampProvider
  val validators = Validators[IO](blockChain)
  val blockGenerator =
    BlockGenerator(blockChain, blockChainConfig, miningConfig, ledger, validators, blockTimestampProvider)
      .unsafeRunSync()

  val bestBlock = blockChain.getBestBlock.unsafeRunSync()
}

class BlockGeneratorSpec extends JbokSpec {
  "BlockGenerator" should {
    "generate correct block with empty transactions" in new BlockGeneratorFixture {

      val result = blockGenerator
        .generateBlockForMining(
          bestBlock,
          Nil,
          Nil,
          Address(testAddress)
        )
        .value
        .unsafeRunSync()

      result.isRight shouldBe true
      val minedNonce = hex"eb49a2da108d63de"
      val minedMixHash = hex"a91c44e62d17005c4b22f6ed116f485ea30d8b63f2429745816093b304eb4f73"
      val miningTimestamp = 1508751768

      val fullBlock = result
        .map(
          pb =>
            pb.block.copy(
              header = pb.block.header.copy(nonce = minedNonce, mixHash = minedMixHash, unixTimestamp = miningTimestamp)
          ))
        .right
        .get

      ledger.executeBlock(fullBlock).value.unsafeRunSync().isRight shouldBe true
      fullBlock.header.extraData shouldBe miningConfig.headerExtraData
    }

    "generate correct block with transactions" in new BlockGeneratorFixture {
      val result = blockGenerator
        .generateBlockForMining(bestBlock, List(signedTransaction), Nil, Address(testAddress))
        .value
        .unsafeRunSync()

      val minedNonce = hex"4139b957dae0488d"
      val minedMixHash = hex"dc25764fb562d778e5d1320f4c3ba4b09021a2603a0816235e16071e11f342ea"
      val miningTimestamp = 1508752265

      val resultBlock = result.right.get.block
      val block = resultBlock.copy(
        header = resultBlock.header.copy(
          nonce = minedNonce,
          mixHash = minedMixHash,
          unixTimestamp = miningTimestamp
        ))
      println(block)
    }
  }
}
