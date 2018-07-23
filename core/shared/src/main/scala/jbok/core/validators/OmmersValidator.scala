package jbok.core.validators

import cats.data.EitherT
import cats.effect.Effect
import jbok.core.BlockChain
import jbok.core.models.{Block, BlockHeader}
import scodec.bits.ByteVector

sealed trait OmmersInvalid extends Invalid
object OmmersInvalid {
  case object OmmersLengthInvalid extends OmmersInvalid
  case object OmmersNotValid extends OmmersInvalid
  case object OmmersUsedBefore extends OmmersInvalid
  case object OmmersAncestorsInvalid extends OmmersInvalid
  case object OmmersDuplicated extends OmmersInvalid
}

class OmmersValidator[F[_]](blockChain: BlockChain[F])(implicit F: Effect[F]) {
  def validate(
      parentHash: ByteVector,
      blockNumber: BigInt,
      ommers: Seq[BlockHeader],
      getBlockHeaderByHash: ByteVector => F[Option[BlockHeader]],
      getNBlocksBack: (ByteVector, Int) => F[List[Block]]
  ): EitherT[F, Invalid, Unit] =
    EitherT.fromEither[F](Right(()))

  def validate(
      parentHash: ByteVector,
      blockNumber: BigInt,
      ommers: List[BlockHeader],
      blockchain: BlockChain[F]
  ): EitherT[F, Invalid, Unit] =
    EitherT.fromEither[F](Right(()))
}
