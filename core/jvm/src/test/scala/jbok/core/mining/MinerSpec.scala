package jbok.core.mining

import jbok.JbokSpec
import jbok.core.BlockChainFixture
import jbok.core.configs.MiningConfig
import jbok.core.ledger.DifficultyCalculator
import jbok.core.models._
import scodec.bits._

trait MinerFixture extends BlockChainFixture {
  val origin = Block(
    BlockHeader(
      parentHash = hex"0000000000000000000000000000000000000000000000000000000000000000",
      ommersHash = hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
      beneficiary = hex"0000000000000000000000000000000000000000",
      stateRoot = hex"56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
      transactionsRoot = hex"56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
      receiptsRoot = hex"56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
      logsBloom = ByteVector.fromValidHex("00" * 256),
      difficulty = UInt256(hex"0400").toBigInt,
      number = 0,
      gasLimit = UInt256(hex"ff1388").toBigInt,
      gasUsed = 0,
      unixTimestamp = 0,
      extraData = hex"00",
      mixHash = ByteVector.fromValidHex("00" * 32),
      nonce = hex"0000000000000042"
    ),
    BlockBody(Nil, Nil)
  )

  val txToMine = SignedTransaction(
    tx = Transaction(
      nonce = BigInt("438553"),
      gasPrice = BigInt("20000000000"),
      gasLimit = BigInt("50000"),
      receivingAddress = Address(hex"3435be928d783b7c48a2c3109cba0d97d680747a"),
      value = BigInt("108516826677274384"),
      payload = ByteVector.empty
    ),
    pointSign = 0x9d.toByte,
    signatureRandom = hex"beb8226bdb90216ca29967871a6663b56bdd7b86cf3788796b52fd1ea3606698",
    signature = hex"2446994156bc1780cb5806e730b171b38307d5de5b9b0d9ad1f9de82e00316b5",
    chainId = 0x3d.toByte
  ).get

  val miningConfig = MiningConfig()

  val difficultyCalc = new DifficultyCalculator(blockChainConfig)

  def calculateGasLimit(parentGas: BigInt): BigInt = {
    val GasLimitBoundDivisor: Int = 1024

    val gasLimitDifference = parentGas / GasLimitBoundDivisor
    parentGas + gasLimitDifference - 1
  }

  val blockForMiningTimestamp = System.currentTimeMillis()

  def blockForMining(parent: BlockHeader): Block = {
    Block(BlockHeader(
      parentHash = parent.hash,
      ommersHash = hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
      beneficiary = miningConfig.coinbase.bytes,
      stateRoot = parent.stateRoot,
      transactionsRoot = parent.transactionsRoot,
      receiptsRoot = parent.receiptsRoot,
      logsBloom = parent.logsBloom,
      difficulty = difficultyCalc.calculateDifficulty(1, blockForMiningTimestamp, parent),
      number = BigInt(1),
      gasLimit = calculateGasLimit(parent.gasLimit),
      gasUsed = BigInt(0),
      unixTimestamp = blockForMiningTimestamp,
      extraData = miningConfig.headerExtraData,
      mixHash = ByteVector.empty,
      nonce = ByteVector.empty
    ), BlockBody(List(txToMine), Nil))
  }


}
class MinerSpec extends JbokSpec {
//  "Miner" should {
//    "mine valid blocks" in new MinerFixture {}
//  }
}
