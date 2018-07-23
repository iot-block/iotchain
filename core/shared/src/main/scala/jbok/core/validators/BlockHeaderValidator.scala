package jbok.core.validators

import cats.data.EitherT
import cats.effect.Effect
import jbok.core.BlockChain
import jbok.core.models.Block

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

class BlockHeaderValidator[F[_]](blockChain: BlockChain[F])(implicit F: Effect[F]) {
  def validate(block: Block): EitherT[F, Invalid, Block] =
    EitherT.fromEither[F](Right(block))
}
