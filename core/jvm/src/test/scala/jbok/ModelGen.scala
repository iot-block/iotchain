package jbok

import BasicGen._
import jbok.core.models.BlockHeader

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
}
