package jbok.core.validators

import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.common.ByteUtils
import jbok.core.ledger.BloomFilter
import jbok.core.models.{Address, BlockHeader, Receipt}
import jbok.core.validators.HeaderInvalid._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import scodec.bits.ByteVector

object HeaderInvalid {
  case object HeaderParentNotFoundInvalid extends Exception("HeaderParentNotFoundInvalid")
  case object HeaderBeneficiaryInvalid    extends Exception("HeaderBeneficiaryInvalid")
  case object HeaderReceiptsHashInvalid   extends Exception("HeaderReceiptsHashInvalid")
  case object HeaderLogBloomInvalid       extends Exception("HeaderLogBloomInvalid")
  case object HeaderNumberInvalid         extends Exception("HeaderNumberInvalid")
  case object HeaderGasLimitInvalid       extends Exception("HeaderGasLimitInvalid")
  case object HeaderGasUsedInvalid        extends Exception("HeaderGasUsedInvalid")
  case object HeaderTimestampInvalid      extends Exception("HeaderTimestampInvalid")
}

object HeaderValidator {
  private val GasLimitBoundDivisor: Int = 1024
  private val MinGasLimit: BigInt       = 5000
  private val MaxGasLimit: BigInt       = BigInt(2).pow(63) - 1

  def preExecValidate[F[_]: Sync](parentOpt: F[Option[BlockHeader]], header: BlockHeader): F[Unit] =
    for {
      parent <- parentOpt.flatMap {
        case Some(p) => Sync[F].pure(p)
        case None    => Sync[F].raiseError[BlockHeader](HeaderParentNotFoundInvalid)
      }
      _ <- preExecValidate[F](parent, header)
    } yield ()

  def preExecValidate[F[_]: Sync](parent: BlockHeader, header: BlockHeader): F[Unit] =
    for {
      _ <- validateBeneficiary[F](header.beneficiary)
      _ <- validateNumber[F](header.number, parent.number)
      _ <- validateGasLimit[F](header.gasLimit, parent.gasLimit)
      _ <- validateGasUsed[F](header.gasUsed, header.gasLimit)
      _ <- validateTimestamp[F](header.unixTimestamp, parent.unixTimestamp)
    } yield ()

  def postExecValidate[F[_]: Sync](
      blockHeader: BlockHeader,
      stateRoot: ByteVector,
      receipts: List[Receipt],
      gasUsed: BigInt
  ): F[Unit] =
    for {
      _ <- validateGasUsed[F](blockHeader, gasUsed)
      _ <- validateStateRoot[F](blockHeader, stateRoot)
      _ <- validateReceipts[F](blockHeader, receipts)
      _ <- validateLogBloom[F](blockHeader, receipts)
    } yield ()

  ////////////////////////////////////
  ////////////////////////////////////

  private[jbok] def validateStateRoot[F[_]](blockHeader: BlockHeader, stateRoot: ByteVector)(
      implicit F: Sync[F]): F[Unit] =
    if (blockHeader.stateRoot == stateRoot) F.unit
    else F.raiseError(new Exception(s"expected stateRoot ${blockHeader.stateRoot}, actually ${stateRoot}"))

  private[jbok] def validateGasUsed[F[_]](blockHeader: BlockHeader, gasUsed: BigInt)(implicit F: Sync[F]): F[Unit] =
    if (blockHeader.gasUsed == gasUsed) F.unit
    else F.raiseError(new Exception(s"expected gasUsed ${blockHeader.gasUsed}, actually ${gasUsed}"))

  /**
    * Validates [[Receipt]] against [[BlockHeader.receiptsRoot]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param blockHeader    Block header to validate
    * @param receipts Receipts to use
    * @return
    */
  private def validateReceipts[F[_]](blockHeader: BlockHeader, receipts: List[Receipt])(implicit F: Sync[F]): F[Unit] =
    MerklePatriciaTrie.calcMerkleRoot[F, Receipt](receipts).flatMap { root =>
      if (root == blockHeader.receiptsRoot) F.unit
      else F.raiseError(HeaderReceiptsHashInvalid)
    }

  /**
    * Validates [[BlockHeader.logsBloom]] against [[Receipt.logsBloomFilter]]
    * based on validations stated in section 4.3.2 of YP
    *
    * @param blockHeader  Block header to validate
    * @param receipts     Receipts to use
    * @return
    */
  private def validateLogBloom[F[_]](blockHeader: BlockHeader, receipts: List[Receipt])(
      implicit F: Sync[F]): F[Unit] = {
    val logsBloomOr =
      if (receipts.isEmpty) BloomFilter.EmptyBloomFilter
      else ByteUtils.or(receipts.map(_.logsBloomFilter): _*)
    if (logsBloomOr equals blockHeader.logsBloom) F.unit
    else F.raiseError(HeaderLogBloomInvalid)
  }

  private def validateBeneficiary[F[_]](beneficiary: ByteVector)(implicit F: Sync[F]): F[Unit] =
    if (beneficiary.length == Address.numBytes || beneficiary.isEmpty) F.unit
    else F.raiseError(HeaderBeneficiaryInvalid)

  private def validateNumber[F[_]](number: BigInt, parentNumber: BigInt)(implicit F: Sync[F]): F[Unit] =
    if (number == parentNumber + 1) F.unit
    else F.raiseError(HeaderNumberInvalid)

  private def validateTimestamp[F[_]](timestamp: Long, parentTimestamp: Long)(implicit F: Sync[F]): F[Unit] =
    if (timestamp > parentTimestamp) F.unit
    else F.raiseError(HeaderTimestampInvalid)

  private def validateGasLimit[F[_]](gasLimit: BigInt, parentGasLimit: BigInt)(implicit F: Sync[F]): F[Unit] =
    if (gasLimit > MaxGasLimit)
      F.raiseError(HeaderGasLimitInvalid)
    else {
      val magic = BigInt(math.floor((parentGasLimit / GasLimitBoundDivisor).toDouble).toInt)
      if (gasLimit >= MinGasLimit && gasLimit < parentGasLimit + magic && gasLimit > parentGasLimit - magic)
        F.unit
      else F.raiseError(HeaderGasLimitInvalid)
    }

  private def validateGasUsed[F[_]](gasUsed: BigInt, gasLimit: BigInt)(implicit F: Sync[F]): F[Unit] =
    if (gasUsed < gasLimit) F.unit
    else F.raiseError(HeaderGasUsedInvalid)
}
