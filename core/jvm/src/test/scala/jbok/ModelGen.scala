package jbok

import BasicGen._
import jbok.core.models.{Account, Address, BlockHeader, UInt256}
import org.scalacheck.Gen

object ModelGen {
  val blockHeaderGen = for {
    parentHash       <- byteVectorOfLengthNGen(32)
    ommersHash       <- byteVectorOfLengthNGen(32)
    beneficiary      <- byteVectorOfLengthNGen(20)
    stateRoot        <- byteVectorOfLengthNGen(32)
    transactionsRoot <- byteVectorOfLengthNGen(32)
    receiptsRoot     <- byteVectorOfLengthNGen(32)
    logsBloom        <- byteVectorOfLengthNGen(50)
    difficulty       <- bigIntGen
    number           <- bigIntGen
    gasLimit         <- bigIntGen
    gasUsed          <- bigIntGen
    unixTimestamp    <- intGen.map(_.abs)
    extraData        <- byteVectorOfLengthNGen(8)
    mixHash          <- byteVectorOfLengthNGen(8)
    nonce            <- byteVectorOfLengthNGen(8)
  } yield
    BlockHeader(
      parentHash = parentHash,
      ommersHash = ommersHash,
      beneficiary = beneficiary,
      stateRoot = stateRoot,
      transactionsRoot = transactionsRoot,
      receiptsRoot = receiptsRoot,
      logsBloom = logsBloom,
      difficulty = difficulty,
      number = number,
      gasLimit = gasLimit,
      gasUsed = gasUsed,
      unixTimestamp = unixTimestamp,
      extraData = extraData,
      mixHash = mixHash,
      nonce = nonce
    )

  val addressGen: Gen[Address] = BasicGen.byteVectorOfLengthNGen(20).map(v => Address(v))

  val uint256Gen: Gen[UInt256] = BasicGen.longGen.map(x => UInt256(x))

  val accountGen: Gen[Account] = for {
    nonce    <- uint256Gen
    balance  <- uint256Gen
    rootHash <- byteVectorOfLengthNGen(32)
    codeHash <- byteVectorOfLengthNGen(32)
  } yield Account(UInt256(0), UInt256(0), rootHash, codeHash)
}
