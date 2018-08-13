package jbok.core.validators

import cats.data.EitherT
import cats.effect.Sync
import jbok.codec.rlp.codecs._
import jbok.codec.rlp.RlpCodec
import jbok.core.ledger.BloomFilter
import jbok.core.models._
import jbok.core.utils.ByteUtils
import jbok.core.validators.BlockInvalid._
import jbok.crypto._

sealed trait BlockInvalid extends Invalid
object BlockInvalid {

  case object BlockTransactionsHashInvalid extends BlockInvalid

  case object BlockOmmersHashInvalid extends BlockInvalid

  case object BlockReceiptsHashInvalid extends BlockInvalid

  case object BlockLogBloomInvalid extends BlockInvalid

}
class BlockValidator[F[_]]()(implicit F: Sync[F]) {

  /**
    * Validates [[BlockHeader.transactionsRoot]] matches [[BlockBody.transactionList]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param block Block to validate
    * @return Block if valid, a Some otherwise
    */
  private def validateTransactionRoot(block: Block): EitherT[F, BlockInvalid, Unit] = {
    val mptValidator = new MPTValidator[F]()
    val isValid = mptValidator.isValid[SignedTransaction](block.header.transactionsRoot, block.body.transactionList)
    EitherT(F.ifM(isValid)(ifTrue = F.pure(Right(Unit)), ifFalse = F.pure(Left(BlockTransactionsHashInvalid))))
  }

  /**
    * Validates [[BlockBody.uncleNodesList]] against [[BlockHeader.ommersHash]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param block Block to validate
    * @return Block if valid, a Some otherwise
    */
  private def validateOmmersHash(block: Block): EitherT[F, BlockInvalid, Unit] =
    if (RlpCodec.encode(block.body.uncleNodesList).require.toByteVector.kec256 equals block.header.ommersHash)
      EitherT.rightT(Unit)
    else EitherT.leftT(BlockOmmersHashInvalid)

  /**
    * Validates [[Receipt]] against [[BlockHeader.receiptsRoot]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param blockHeader    Block header to validate
    * @param receipts Receipts to use
    * @return
    */
  private def validateReceipts(blockHeader: BlockHeader, receipts: List[Receipt]): EitherT[F, BlockInvalid, Unit] = {

    val mptValidator = new MPTValidator[F]()
    val isValid = mptValidator.isValid[Receipt](blockHeader.receiptsRoot, receipts)
    EitherT(F.ifM(isValid)(ifTrue = F.pure(Right(Unit)), ifFalse = F.pure(Left(BlockReceiptsHashInvalid))))
  }

  /**
    * Validates [[BlockHeader.logsBloom]] against [[Receipt.logsBloomFilter]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param blockHeader  Block header to validate
    * @param receipts     Receipts to use
    * @return
    */
  private def validateLogBloom(blockHeader: BlockHeader, receipts: List[Receipt]): EitherT[F, BlockInvalid, Unit] = {
    val logsBloomOr =
      if (receipts.isEmpty) BloomFilter.EmptyBloomFilter
      else ByteUtils.or(receipts.map(_.logsBloomFilter): _*)
    if (logsBloomOr equals blockHeader.logsBloom) EitherT.rightT(Unit)
    else EitherT.leftT(BlockLogBloomInvalid)
  }

  /**
    * This method allows validate a Block. It only perfoms the following validations (stated on
    * section 4.3.2 of YP):
    *   - BlockValidator.validateTransactionRoot
    *   - BlockValidator.validateOmmersHash
    *   - BlockValidator.validateReceipts
    *   - BlockValidator.validateLogBloom
    *
    * @param block    Block to validate
    * @param receipts Receipts to be in validation process
    * @return The block if validations are ok, error otherwise
    */
  def validate(block: Block, receipts: List[Receipt]): EitherT[F, BlockInvalid, Unit] =
    for {
      _ <- validateHeaderAndBody(block)
      _ <- validateBlockAndReceipts(block.header, receipts)
    } yield Unit

  /**
    * This method allows validate that a BlockHeader matches a BlockBody. It only perfoms the following validations (stated on
    * section 4.3.2 of YP):
    *   - BlockValidator.validateTransactionRoot
    *   - BlockValidator.validateOmmersHash
    *
    * @param block to validate
    * @return The block if the header matched the body, error otherwise
    */
  def validateHeaderAndBody(block: Block): EitherT[F, BlockInvalid, Block] =
    for {
      _ <- validateTransactionRoot(block)
      _ <- validateOmmersHash(block)
    } yield block

  /**
    * This method allows validations of the block with its associated receipts.
    * It only perfoms the following validations (stated on section 4.3.2 of YP):
    *   - BlockValidator.validateReceipts
    *   - BlockValidator.validateLogBloom
    *
    * @param blockHeader    Block header to validate
    * @param receipts Receipts to be in validation process
    * @return The block if validations are ok, error otherwise
    */
  def validateBlockAndReceipts(blockHeader: BlockHeader, receipts: List[Receipt]): EitherT[F, BlockInvalid, Unit] =
    for {
      _ <- validateReceipts(blockHeader, receipts)
      _ <- validateLogBloom(blockHeader, receipts)
    } yield Unit
}
