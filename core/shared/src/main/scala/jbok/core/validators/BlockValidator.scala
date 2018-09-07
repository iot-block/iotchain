package jbok.core.validators

import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.core.ledger.BloomFilter
import jbok.core.models._
import jbok.core.utils.ByteUtils
import jbok.core.validators.BlockInvalid._
import jbok.crypto._
import scodec.bits.ByteVector

object BlockInvalid {
  case object BlockTransactionsHashInvalid extends Exception("BlockTransactionsHashInvalid")
  case object BlockOmmersHashInvalid       extends Exception("BlockOmmersHashInvalid")
  case object BlockReceiptsHashInvalid     extends Exception("BlockReceiptsHashInvalid")
  case object BlockLogBloomInvalid         extends Exception("BlockLogBloomInvalid")
}

class BlockValidator[F[_]]()(implicit F: Sync[F]) {
  def validate(block: Block, receipts: List[Receipt]): F[Unit] =
    for {
      _ <- validateHeaderAndBody(block)
      _ <- validateBlockAndReceipts(block.header, receipts)
    } yield ()

  /**
    * This method allows validate that a BlockHeader matches a BlockBody. It only perfoms the following validations (stated on
    * section 4.3.2 of YP):
    *   - BlockValidator.validateTransactionRoot
    *   - BlockValidator.validateOmmersHash
    *
    * @param block to validate
    * @return The block if the header matched the body, error otherwise
    */
  def validateHeaderAndBody(block: Block): F[Unit] =
    for {
      _ <- validateTransactionRoot(block)
      _ <- validateOmmersHash(block)
    } yield ()

  /**
    * This method allows validations of the block with its associated receipts.
    * It only performs the following validations (stated on section 4.3.2 of YP):
    *   - BlockValidator.validateReceipts
    *   - BlockValidator.validateLogBloom
    *
    * @param blockHeader    Block header to validate
    * @param receipts Receipts to be in validation process
    * @return The block if validations are ok, error otherwise
    */
  def validateBlockAndReceipts(
      blockHeader: BlockHeader,
      receipts: List[Receipt]
  ): F[Unit] =
    for {
      _ <- validateReceipts(blockHeader, receipts)
      _ <- validateLogBloom(blockHeader, receipts)
    } yield ()

  def postExecuteValidate(
      blockHeader: BlockHeader,
      stateRoot: ByteVector,
      receipts: List[Receipt],
      gasUsed: BigInt
  ): F[Unit] =
    for {
      _ <- validateGasUsed(blockHeader, gasUsed)
      _ <- validateStateRoot(blockHeader, stateRoot)
      _ <- validateReceipts(blockHeader, receipts)
      _ <- validateLogBloom(blockHeader, receipts)
    } yield ()

  private[jbok] def validateStateRoot(blockHeader: BlockHeader, stateRoot: ByteVector): F[Unit] =
    if (blockHeader.stateRoot == stateRoot) F.unit
    else F.raiseError(new Exception(s"expected stateRoot ${blockHeader.stateRoot}, actually ${stateRoot}"))

  private[jbok] def validateGasUsed(blockHeader: BlockHeader, gasUsed: BigInt): F[Unit] =
    if (blockHeader.gasUsed == gasUsed) F.unit
    else F.raiseError(new Exception(s"expected gasUsed ${blockHeader.gasUsed}, actually ${gasUsed}"))

  /**
    * Validates [[BlockHeader.transactionsRoot]] matches [[BlockBody.transactionList]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param block Block to validate
    * @return Block if valid, a Some otherwise
    */
  private def validateTransactionRoot(block: Block): F[Unit] = {
    val mptValidator = new MPTValidator[F]()
    val isValid      = mptValidator.isValid[SignedTransaction](block.header.transactionsRoot, block.body.transactionList)
    F.ifM(isValid)(ifTrue = F.unit, ifFalse = F.raiseError(BlockTransactionsHashInvalid))
  }

  /**
    * Validates [[BlockBody.uncleNodesList]] against [[BlockHeader.ommersHash]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param block Block to validate
    * @return Block if valid, a Some otherwise
    */
  private def validateOmmersHash(block: Block): F[Unit] =
    if (RlpCodec.encode(block.body.uncleNodesList).require.toByteVector.kec256 equals block.header.ommersHash) F.unit
    else F.raiseError(BlockOmmersHashInvalid)

  /**
    * Validates [[Receipt]] against [[BlockHeader.receiptsRoot]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param blockHeader    Block header to validate
    * @param receipts Receipts to use
    * @return
    */
  private def validateReceipts(blockHeader: BlockHeader, receipts: List[Receipt]): F[Unit] = {

    val mptValidator = new MPTValidator[F]()
    val isValid      = mptValidator.isValid[Receipt](blockHeader.receiptsRoot, receipts)
    F.ifM(isValid)(ifTrue = F.unit, ifFalse = F.raiseError(BlockReceiptsHashInvalid))
  }

  /**
    * Validates [[BlockHeader.logsBloom]] against [[Receipt.logsBloomFilter]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param blockHeader  Block header to validate
    * @param receipts     Receipts to use
    * @return
    */
  private def validateLogBloom(blockHeader: BlockHeader, receipts: List[Receipt]): F[Unit] = {
    val logsBloomOr =
      if (receipts.isEmpty) BloomFilter.EmptyBloomFilter
      else ByteUtils.or(receipts.map(_.logsBloomFilter): _*)
    if (logsBloomOr equals blockHeader.logsBloom) F.unit
    else F.raiseError(BlockLogBloomInvalid)
  }
}
