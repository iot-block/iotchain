package jbok.core.validators

import cats.effect.Sync
import cats.implicits._
import jbok.core.models.{Block, BlockHeader}

object BlockValidator {
  def preExecValidate[F[_]: Sync](parentHeader: BlockHeader, block: Block): F[Unit] =
    for {
      _ <- HeaderValidator.preExecValidate[F](parentHeader, block.header)
      _ <- BodyValidator.validate[F](block)
    } yield ()
}
