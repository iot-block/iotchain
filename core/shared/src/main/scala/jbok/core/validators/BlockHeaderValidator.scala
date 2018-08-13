package jbok.core.validators

import cats.data.EitherT
import cats.effect.Effect
import cats.implicits._
import jbok.core.BlockChain
import jbok.core.configs.BlockChainConfig
import jbok.core.ledger.DifficultyCalculator
import jbok.core.models.BlockHeader
import jbok.core.validators.BlockHeaderInvalid._
import scodec.bits.ByteVector

sealed trait BlockHeaderInvalid extends Invalid

object BlockHeaderInvalid {
  case object HeaderParentNotFoundInvalid extends BlockHeaderInvalid
  case object HeaderExtraDataInvalid extends BlockHeaderInvalid
  case object DaoHeaderExtraDataInvalid extends BlockHeaderInvalid
  case object HeaderTimestampInvalid extends BlockHeaderInvalid
  case object HeaderDifficultyInvalid extends BlockHeaderInvalid
  case object HeaderGasUsedInvalid extends BlockHeaderInvalid
  case object HeaderGasLimitInvalid extends BlockHeaderInvalid
  case object HeaderNumberInvalid extends BlockHeaderInvalid
  case object HeaderPoWInvalid extends BlockHeaderInvalid
}

class BlockHeaderValidator[F[_]](blockChain: BlockChain[F], blockChainConfig: BlockChainConfig)(implicit F: Effect[F]) {
  private val MaxExtraDataSize: Int = 32
  private val GasLimitBoundDivisor: Int = 1024
  private val MinGasLimit: BigInt = 5000
  private val MaxGasLimit = Long.MaxValue
  private val MaxPowCaches: Int = 2

  private def validateNumber(number: BigInt, parentNumber: BigInt): EitherT[F, BlockHeaderInvalid, BigInt] =
    if (number == parentNumber + 1) EitherT.rightT(number)
    else EitherT.leftT(HeaderNumberInvalid)

  private def validateDifficulty(difficulty: BigInt,
                                 timestamp: Long,
                                 number: BigInt,
                                 parentHeader: BlockHeader): EitherT[F, BlockHeaderInvalid, BigInt] = {
    val diffCal = new DifficultyCalculator(blockChainConfig)
    if (difficulty == diffCal.calculateDifficulty(number, timestamp, parentHeader)) EitherT.rightT(difficulty)
    else EitherT.leftT(HeaderDifficultyInvalid)
  }

  private def validateTimestamp(timestamp: Long, parentTimestamp: Long): EitherT[F, BlockHeaderInvalid, Long] =
    if (timestamp > parentTimestamp) EitherT.rightT(timestamp)
    else EitherT.leftT(HeaderTimestampInvalid)

  /**
    *
    * @param gasLimit
    * @param number
    * @param parentGasLimit
    * @return
    */
  private def validateGasLimit(gasLimit: BigInt,
                               number: BigInt,
                               parentGasLimit: BigInt): EitherT[F, BlockHeaderInvalid, BigInt] =
    if (gasLimit > MaxGasLimit && number >= blockChainConfig.eip106BlockNumber)
      EitherT.leftT(HeaderGasLimitInvalid)
    else {
      val magic = BigInt(math.floor((parentGasLimit / GasLimitBoundDivisor).toDouble).toInt)
      if (gasLimit >= MinGasLimit && gasLimit < parentGasLimit + magic && gasLimit > parentGasLimit - magic)
        EitherT.rightT(gasLimit)
      else EitherT.leftT(HeaderGasLimitInvalid)
    }

  private def validatePow(nonce: ByteVector, parentNonce: ByteVector): EitherT[F, BlockHeaderInvalid, ByteVector] =
    EitherT.rightT(nonce)

  private def validateExtraData(extraData: ByteVector): EitherT[F, BlockHeaderInvalid, ByteVector] =
    if (extraData.length <= MaxExtraDataSize) EitherT.rightT(extraData)
    else EitherT.leftT(HeaderExtraDataInvalid)

  private def validateGasUsed(gasUsed: BigInt, gasLimit: BigInt): EitherT[F, BlockHeaderInvalid, BigInt] =
    if (gasUsed < gasLimit) EitherT.rightT(gasUsed)
    else EitherT.leftT(HeaderGasUsedInvalid)

  private def getParentBlockHeader(
      blockChain: BlockChain[F],
      parentHash: ByteVector,
      getBlockHeaderByHash: ByteVector => F[Option[BlockHeader]]): EitherT[F, BlockHeaderInvalid, BlockHeader] =
    EitherT(getBlockHeaderByHash(parentHash).map {
      case Some(header) => Right(header)
      case None         => Left(HeaderParentNotFoundInvalid)
    })

  def validate(header: BlockHeader): EitherT[F, BlockHeaderInvalid, BlockHeader] =
    validate(header, blockChain.getBlockHeaderByHash)

  def validate(header: BlockHeader,
               getBlockHeader: ByteVector => F[Option[BlockHeader]]): EitherT[F, BlockHeaderInvalid, BlockHeader] =
    for {
      parentHeader <- getParentBlockHeader(blockChain, header.parentHash, getBlockHeader)
      _ <- validateNumber(header.number, parentHeader.number)
      _ <- validateDifficulty(header.difficulty, header.unixTimestamp, header.number, parentHeader)
      _ <- validateTimestamp(header.unixTimestamp, parentHeader.unixTimestamp)
      _ <- validateGasLimit(header.gasLimit, header.number, parentHeader.gasLimit)
      _ <- validatePow(header.nonce, parentHeader.nonce)
      _ <- validateExtraData(header.extraData)
      _ <- validateGasUsed(header.gasUsed, header.gasLimit)
    } yield header
}
