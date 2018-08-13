package jbok.core.validators

import cats.data.EitherT
import cats.effect.Effect
import cats.implicits._
import jbok.core.BlockChain
import jbok.core.configs.BlockChainConfig
import jbok.core.models.{Block, BlockHeader}
import jbok.core.validators.OmmersInvalid._
import scodec.bits.ByteVector

sealed trait OmmersInvalid extends Invalid

object OmmersInvalid {

  case object OmmersLengthInvalid extends OmmersInvalid

  case object OmmersNotValid extends OmmersInvalid

  case object OmmersUsedBefore extends OmmersInvalid

  case object OmmersAncestorsInvalid extends OmmersInvalid

  case object OmmersDuplicated extends OmmersInvalid

}

class OmmersValidator[F[_]](blockChain: BlockChain[F], blockChainConfig: BlockChainConfig)(implicit F: Effect[F]) {
  private def validateLength(length: Int): EitherT[F, Invalid, Int] =
    if (length <= 2) EitherT.rightT(length)
    else EitherT.leftT(OmmersLengthInvalid)

  private def validateDuplicated(ommers: List[BlockHeader]): EitherT[F, Invalid, List[BlockHeader]] =
    if (ommers.distinct.length == ommers.length) EitherT.rightT(ommers)
    else EitherT.leftT(OmmersDuplicated)

  private def validateOmmersAncestors(
      ommers: List[BlockHeader],
      parentHash: ByteVector,
      blockNumber: BigInt,
      getNBlocksBack: (ByteVector, Int) => F[List[Block]]): EitherT[F, Invalid, List[BlockHeader]] = {
    val numberOfBlocks = blockNumber.min(6).toInt

    def isSibling(ommerHeader: BlockHeader, parent: Block): Boolean =
      ommerHeader.parentHash == parent.header.parentHash && !ommerHeader.equals(parent.header) && !parent.body.uncleNodesList
        .contains(ommerHeader)

    def isKen(ommer: BlockHeader) =
      for {
        ancestors <- getNBlocksBack(parentHash, numberOfBlocks)
        b = ancestors.foldRight(false)((block, z) =>
          z || (isSibling(ommer, block) && ancestors.forall(!_.body.uncleNodesList.contains(ommer))))
      } yield b

    val r = ommers.foldLeftM(true)((z, header) => isKen(header).map(z && _))
    EitherT(F.ifM(r)(ifTrue = F.pure(Right(ommers)), ifFalse = F.pure(Left(OmmersAncestorsInvalid))))
  }

  private def validateOmmerHeader(ommers: List[BlockHeader]): EitherT[F, Invalid, List[BlockHeader]] = {
    val headerValidator = new BlockHeaderValidator(blockChain, blockChainConfig)
    val r = ommers.foldLeftM(true)((z, header) => headerValidator.validate(header).isRight.map(_ && z))
    EitherT(F.ifM(r)(ifTrue = F.pure(Right(ommers)), ifFalse = F.pure(Left(OmmersNotValid))))
  }

  def validate(
      parentHash: ByteVector,
      blockNumber: BigInt,
      ommers: List[BlockHeader],
      getBlockHeaderByHash: ByteVector => F[Option[BlockHeader]],
      getNBlocksBack: (ByteVector, Int) => F[List[Block]]
  ): EitherT[F, Invalid, Unit] =
    for {
      _ <- validateLength(ommers.length)
      _ <- validateDuplicated(ommers)
      _ <- validateOmmerHeader(ommers)
      _ <- validateOmmersAncestors(ommers, parentHash, blockNumber, getNBlocksBack)
    } yield Unit

  def validate(
      parentHash: ByteVector,
      blockNumber: BigInt,
      ommers: List[BlockHeader]
  ): EitherT[F, Invalid, Unit] = {
    val getBlockHeaderByHash: ByteVector => F[Option[BlockHeader]] = blockChain.getBlockHeaderByHash
    val getNBlocksBack: (ByteVector, Int) => F[List[Block]] = (_, n) =>
      ((blockNumber - n) until blockNumber).toList.map(blockChain.getBlockByNumber).sequence.map(_.flatten)

    validate(parentHash, blockNumber, ommers, getBlockHeaderByHash, getNBlocksBack)
  }
}
