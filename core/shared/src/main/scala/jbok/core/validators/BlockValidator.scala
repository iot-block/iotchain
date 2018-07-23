package jbok.core.validators

import cats.data.EitherT
import cats.effect.Sync
import jbok.core.models.{Block, BlockBody, BlockHeader, Receipt}

sealed trait BlockInvalid
case object BlockTransactionsHashInvalid extends BlockInvalid
case object BlockOmmersHashInvalid extends BlockInvalid
case object BlockReceiptsHashInvalid extends BlockInvalid
case object BlockLogBloomInvalid extends BlockInvalid

class BlockValidator[F[_]](implicit F: Sync[F]) {
  def validateHeaderAndBody(block: Block): EitherT[F, Invalid, Block] =
    EitherT.fromEither[F](Right(block))

  def validateBlockAndReceipts(blockHeader: BlockHeader, receipts: List[Receipt]): F[Either[BlockInvalid, Unit]] =
    F.pure(Right(()))

  //////////////////
  //////////////////
}
