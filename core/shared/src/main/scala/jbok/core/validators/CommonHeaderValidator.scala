package jbok.core.validators

import cats.effect.Sync
import cats.implicits._
import jbok.core.History
import jbok.core.models.BlockHeader
import jbok.core.validators.CommonHeaderInvalid._
import scodec.bits.ByteVector

object CommonHeaderInvalid {
  case object HeaderParentNotFoundInvalid extends Exception("HeaderParentNotFoundInvalid")
  case object HeaderTimestampInvalid      extends Exception("HeaderTimestampInvalid")
  case object HeaderGasUsedInvalid        extends Exception("HeaderGasUsedInvalid")
  case object HeaderGasLimitInvalid       extends Exception("HeaderGasLimitInvalid")
  case object HeaderNumberInvalid         extends Exception("HeaderNumberInvalid")
}

class CommonHeaderValidator[F[_]](history: History[F])(implicit F: Sync[F]) {
  private val GasLimitBoundDivisor: Int = 1024
  private val MinGasLimit: BigInt       = 5000
  private val MaxGasLimit               = BigInt(2).pow(63) - 1

  private def validateNumber(number: BigInt, parentNumber: BigInt): F[Unit] =
    if (number == parentNumber + 1) F.unit
    else F.raiseError(HeaderNumberInvalid)

  private def validateTimestamp(timestamp: Long, parentTimestamp: Long): F[Unit] =
    if (timestamp > parentTimestamp) F.unit
    else F.raiseError(HeaderTimestampInvalid)

  private def validateGasLimit(gasLimit: BigInt, parentGasLimit: BigInt): F[Unit] =
    if (gasLimit > MaxGasLimit)
      F.raiseError(HeaderGasLimitInvalid)
    else {
      val magic = BigInt(math.floor((parentGasLimit / GasLimitBoundDivisor).toDouble).toInt)
      if (gasLimit >= MinGasLimit && gasLimit < parentGasLimit + magic && gasLimit > parentGasLimit - magic)
        F.unit
      else F.raiseError(HeaderGasLimitInvalid)
    }

  private def validateGasUsed(gasUsed: BigInt, gasLimit: BigInt): F[Unit] =
    if (gasUsed < gasLimit) F.unit
    else F.raiseError(HeaderGasUsedInvalid)

  private def getParentBlockHeader(parentHash: ByteVector,
                                   getBlockHeader: ByteVector => F[Option[BlockHeader]]): F[BlockHeader] =
    getBlockHeader(parentHash).flatMap {
      case Some(header) => F.pure(header)
      case None         => F.raiseError(HeaderParentNotFoundInvalid)
    }

  def validate(header: BlockHeader): F[BlockHeader] = validate(header, history.getBlockHeaderByHash _)

  def validate(header: BlockHeader, getBlockHeader: ByteVector => F[Option[BlockHeader]]): F[BlockHeader] =
    for {
      parentHeader <- getParentBlockHeader(header.parentHash, getBlockHeader)
      _            <- validateNumber(header.number, parentHeader.number)
      _            <- validateTimestamp(header.unixTimestamp, parentHeader.unixTimestamp)
      _            <- validateGasLimit(header.gasLimit, parentHeader.gasLimit)
      _            <- validateGasUsed(header.gasUsed, header.gasLimit)
    } yield parentHeader
}
