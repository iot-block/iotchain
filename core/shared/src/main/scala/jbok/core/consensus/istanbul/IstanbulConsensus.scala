package jbok.core.consensus.istanbul

import cats.effect.ConcurrentEffect
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.consensus.{Consensus, Snapshot}
import jbok.core.consensus.Consensus.Result
import jbok.core.consensus.istanbul.IstanbulInvalid._
import jbok.core.ledger.TypedBlock
import jbok.core.ledger.TypedBlock.MinedBlock
import jbok.core.messages.IstanbulMessage
import jbok.core.models.{Address, Block, BlockHeader}
import jbok.core.pool.BlockPool
import jbok.core.validators.HeaderInvalid.HeaderParentNotFoundInvalid
import jbok.crypto._
import scodec.bits.ByteVector

import scala.util.Random

object IstanbulInvalid {
  case object TransactionRootInvalid    extends Exception("TransactionRootInvalid")
  case object OmmersHashInvalid         extends Exception("OmmersHashInvalid")
  case object BlockNumberInvalid        extends Exception("BlockNumberInvalid")
  case object BlockTimestampInvalid     extends Exception("BlockTimestampInvalid")
  case object HeaderExtraInvalid        extends Exception("HeaderExtraInvalid")
  case object DifficultyInvalid         extends Exception("DifficultyInvalid")
  case object SignerUnauthorizedInvalid extends Exception("SignerUnauthorizedInvalid")
  case object CommittedSealInvalid      extends Exception("CommittedSealInvalid")
}

class IstanbulConsensus[F[_]](val blockPool: BlockPool[F], val istanbul: Istanbul[F])(implicit F: ConcurrentEffect[F])
    extends Consensus[F](istanbul.history, blockPool) {

  private def validateExtra(header: BlockHeader, snapshot: Snapshot): F[IstanbulExtra] =
    for {
      extra <- header.extraAs[F, IstanbulExtra]
      _     <- validateCommittedSeals(header, snapshot, extra)
    } yield extra

  private def validateHeader(parentHeader: BlockHeader, header: BlockHeader): F[Unit] =
    for {
      _ <- if (header.unixTimestamp > System.currentTimeMillis()) F.raiseError(BlockTimestampInvalid) else F.unit
      _ <- if (parentHeader.unixTimestamp + istanbul.config.period.toMillis > header.unixTimestamp)
        F.raiseError(BlockTimestampInvalid)
      else F.unit
      _ <- if (header.ommersHash == ByteVector.empty) F.unit else F.raiseError(OmmersHashInvalid)
      _ <- if (header.difficulty == istanbul.config.defaultDifficulty) F.unit else F.raiseError(DifficultyInvalid)
      _ <- if (header.number == 0) { F.unit } else {
        for {
          _ <- Option(header.number) match {
            case Some(n) => if (n >= 0) F.unit else F.raiseError(BlockNumberInvalid)
            case None    => F.raiseError(BlockNumberInvalid)
          }
          snap <- istanbul.applyHeaders(header.number - 1, parentHeader.hash, Nil, Nil)
          _    <- validateSigner(header, snap)
          _    <- validateExtra(header, snap)
        } yield ()
      }

    } yield ()

  private def validateCommittedSeals(header: BlockHeader, snapshot: Snapshot, extraData: IstanbulExtra): F[Unit] =
    for {
      _ <- if (header.number == 0) F.raiseError(BlockNumberInvalid) else F.unit
      proposalSeal = prepareCommittedSeal(header.hash)
      // recover the original address by seal and block hash
      validSealAddrs <- extraData.committedSigs
        .map(seal => F.fromOption(istanbul.ecrecover(proposalSeal, seal), CommittedSealInvalid))
        .sequence
      validSeals = validSealAddrs.filter(pk => snapshot.signers.contains(Address(pk.bytes.kec256))).distinct
      _ <- if (validSeals.size <= 2 * (Math.ceil(snapshot.signers.size / 3.0).toInt - 1))
        F.raiseError(CommittedSealInvalid)
      else F.unit
    } yield ()

  private def prepareCommittedSeal(hash: ByteVector): ByteVector =
    hash ++ ByteVector(IstanbulMessage.msgCommitCode)

  private def validateSigner(header: BlockHeader, snapshot: Snapshot): F[Unit] =
    for {
      _      <- if (header.number == 0) F.raiseError(BlockNumberInvalid) else F.unit
      signer <- F.delay(Istanbul.ecrecover(header))
      _      <- if (snapshot.signers.contains(signer)) F.unit else F.raiseError(SignerUnauthorizedInvalid)
    } yield ()

  override def verify(block: Block): F[Unit] =
    history.getBlockHeaderByHash(block.header.parentHash).flatMap {
      case Some(parentHeader) => validateHeader(parentHeader, block.header)
      case None               => F.raiseError(HeaderParentNotFoundInvalid)
    }

  private def calcDifficulty(blockTime: Long, parentHeader: BlockHeader): F[BigInt] =
    F.pure(istanbul.config.defaultDifficulty)

  def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader] = Nil): F[BlockHeader] =
    for {
      parent <- parentOpt.fold(history.getBestBlock)(_.pure[F])
      blockNumber = parent.header.number + 1
      timestamp   = parent.header.unixTimestamp + istanbul.config.period.toMillis
      snap       <- istanbul.applyHeaders(blockNumber - 1, parent.header.hash, Nil, Nil)
      difficulty <- calcDifficulty(timestamp, parent.header)
      candidates <- istanbul.candidates.get
      candidate = Random.shuffle(candidates.toSeq).headOption
      extraData = prepareExtra(snap)
    } yield
      BlockHeader(
        parentHash = parent.header.hash,
        ommersHash = ByteVector.empty,
        beneficiary = candidate match {
          case Some((address, _)) => address.bytes
          case None               => ByteVector.empty
        },
        stateRoot = ByteVector.empty,
        transactionsRoot = ByteVector.empty,
        receiptsRoot = ByteVector.empty,
        logsBloom = ByteVector.empty,
        difficulty = difficulty,
        number = blockNumber,
        gasLimit = calcGasLimit(parent.header.gasLimit),
        gasUsed = 0,
        unixTimestamp = timestamp,
        extra = extraData.asValidBytes
      )

  private def calcGasLimit(parentGas: BigInt): BigInt = {
    val GasLimitBoundDivisor: Int = 1024
    val gasLimitDifference        = parentGas / GasLimitBoundDivisor
    parentGas + gasLimitDifference - 1
  }

  private def prepareExtra(snapshot: Snapshot): IstanbulExtra =
    IstanbulExtra(
      validators = snapshot.getSigners,
      proposerSig = ByteVector.empty,
      committedSigs = List.empty
    )

  override def postProcess(executed: TypedBlock.ExecutedBlock[F]): F[TypedBlock.ExecutedBlock[F]] = F.pure(executed)

  override def mine(executed: TypedBlock.ExecutedBlock[F]): F[TypedBlock.MinedBlock] =
    if (executed.block.header.number == 0) {
      F.raiseError(new Exception("mining the genesis block is not supported"))
    } else {
      for {
        snap <- istanbul.applyHeaders(executed.block.header.number - 1, executed.block.header.parentHash, Nil)
        mined <- if (!snap.signers.contains(istanbul.signer)) {
          F.raiseError(new Exception("unauthorized"))
        } else {
          for {
            sigHash <- F.delay(Istanbul.sigHash(executed.block.header))
            seal    <- istanbul.sign(sigHash)
            extra = IstanbulExtra(snap.getSigners, ByteVector(seal.bytes), List.empty)
            header = Istanbul
              .filteredHeader(executed.block.header, false)
//              .copy(extraData = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra).require.bytes)
          } yield MinedBlock(executed.block.copy(header = header), executed.receipts)
        }
      } yield mined
    }

  override def run(block: Block): F[Result] = F.pure(Consensus.Stash(block))

  override def resolveBranch(headers: List[BlockHeader]): F[Consensus.BranchResult] = F.pure(Consensus.NoChainSwitch)
}
