package jbok.core.consensus.istanbul

import cats.effect.ConcurrentEffect
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.consensus.Consensus
import jbok.core.models.{Address, Block, BlockHeader}
import jbok.core.pool.BlockPool
import scodec.bits.ByteVector
import jbok.core.consensus.istanbul.IstanbulInvalid._
import cats.implicits._
import jbok.core.consensus.Consensus.Result
import jbok.core.ledger.TypedBlock
import jbok.core.ledger.TypedBlock.MinedBlock
import jbok.core.messages.IstanbulMessage
import jbok.core.validators.HeaderInvalid.HeaderParentNotFoundInvalid
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, ECDSA, KeyPair, Signature}

import scala.util.Random

object IstanbulInvalid {
  case object TransactionRootInvalid    extends Exception("TransactionRootInvalid")
  case object OmmersHashInvalid         extends Exception("OmmersHashInvalid")
  case object BlockNumberInvalid        extends Exception("BlockNumberInvalid")
  case object BlockTimestampInvalid     extends Exception("BlockTimestampInvalid")
  case object HeaderExtraInvalid        extends Exception("HeaderExtraInvalid")
  case object HeaderNonceInvalid        extends Exception("HeaderNonceInvalid")
  case object HeaderMixhashInvalid      extends Exception("HeaderMixhashInvalid")
  case object DifficultyInvalid         extends Exception("DifficultyInvalid")
  case object SignerUnauthorizedInvalid extends Exception("SignerUnauthorizedInvalid")
  case object CommittedSealInvalid      extends Exception("CommittedSealInvalid")
}

class IstanbulConsensus[F[_]](val blockPool: BlockPool[F], val istanbul: Istanbul[F])(implicit F: ConcurrentEffect[F])
    extends Consensus[F](istanbul.history, blockPool) {

  private def validateExtra(header: BlockHeader, snapshot: Snapshot): F[IstanbulExtra] =
    for {
      _         <- if (header.extraData.length < Istanbul.extraVanity) F.raiseError(HeaderExtraInvalid) else F.unit
      extraData <- F.delay(Istanbul.extractIstanbulExtra(header))
      _         <- validateCommittedSeals(header, snapshot, extraData)
    } yield extraData

  private def validateHeader(parentHeader: BlockHeader, header: BlockHeader): F[Unit] =
    for {
      _ <- if (header.unixTimestamp > System.currentTimeMillis()) F.raiseError(BlockTimestampInvalid) else F.unit
      _ <- if (parentHeader.unixTimestamp + istanbul.config.period.toMillis > header.unixTimestamp)
        F.raiseError(BlockTimestampInvalid)
      else F.unit
      _ <- if (header.nonce != ByteVector.empty && header.nonce != Istanbul.nonceDropVote && header.nonce != Istanbul.nonceAuthVote)
        F.raiseError(HeaderNonceInvalid)
      else F.unit
      _ <- if (header.mixHash != Istanbul.mixDigest) F.raiseError(HeaderMixhashInvalid) else F.unit
      _ <- if (header.ommersHash == ByteVector.empty) F.unit else F.raiseError(OmmersHashInvalid)
      _ <- if (header.difficulty == istanbul.config.defaultDifficulty) F.unit else F.raiseError(DifficultyInvalid)
      _ <- if (header.number == 0) { F.unit } else {
        for {
          _ <- Option(header.number) match {
            case Some(n) => if (n >= 0) F.unit else F.raiseError(BlockNumberInvalid)
            case None    => F.raiseError(BlockNumberInvalid)
          }
          snap      <- istanbul.applyHeaders(header.number - 1, parentHeader.hash, Nil, Nil)
          _         <- validateSigner(header, snap)
          extraData <- validateExtra(header, snap)
        } yield ()
      }

    } yield ()

  private def validateCommittedSeals(header: BlockHeader, snapshot: Snapshot, extraData: IstanbulExtra): F[Unit] =
    for {
      _ <- if (header.number == 0) F.raiseError(BlockNumberInvalid) else F.unit
      proposalSeal = prepareCommittedSeal(header.hash)
      // recover the original address by seal and block hash
      validSealAddrs <- extraData.committedSeals
        .map(
          seal =>
            F.fromOption(istanbul.ecrecover(proposalSeal,CryptoSignature(seal.toArray)),
                         CommittedSealInvalid))
        .sequence
      validSeals = validSealAddrs.filter(pk => snapshot.validatorSet.contains(Address(pk.bytes.kec256))).distinct
      _ <- if (validSeals.size <= 2 * snapshot.f) F.raiseError(CommittedSealInvalid) else F.unit
    } yield ()

  private def prepareCommittedSeal(hash: ByteVector): ByteVector =
    hash ++ ByteVector(IstanbulMessage.msgCommitCode)

  private def validateSigner(header: BlockHeader, snapshot: Snapshot): F[Unit] =
    for {
      _      <- if (header.number == 0) F.raiseError(BlockNumberInvalid) else F.unit
      signer <- F.delay(Istanbul.ecrecover(header))
      _      <- if (snapshot.validatorSet.contains(signer)) F.unit else F.raiseError(SignerUnauthorizedInvalid)
    } yield ()

  override def verify(block: Block): F[Unit] =
    history.getBlockHeaderByHash(block.header.parentHash).flatMap {
      case Some(parentHeader) => validateHeader(parentHeader, block.header)
      case None               => F.raiseError(HeaderParentNotFoundInvalid)
    }

  private def calcDifficulty(blockTime: Long, parentHeader: BlockHeader): F[BigInt] =
    F.pure(istanbul.config.defaultDifficulty)

  private def calcBlockMinerReward(blockNumber: BigInt, ommersCount: Int): F[BigInt] = F.pure(BigInt(0))

  private def calcOmmerMinerReward(blockNumber: BigInt, ommerNumber: BigInt): F[BigInt] = F.pure(BigInt(0))

  private def getTimestamp: F[Long] = F.pure(System.currentTimeMillis())

  def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader] = Nil): F[BlockHeader] =
    for {
      parent <- parentOpt.fold(history.getBestBlock)(_.pure[F])
      blockNumber = parent.header.number + 1
      timestamp   = parent.header.unixTimestamp + istanbul.config.period.toMillis
      snap       <- istanbul.applyHeaders(blockNumber - 1, parent.header.hash, Nil, Nil)
      difficulty <- calcDifficulty(timestamp, parent.header)
      candidates <- istanbul.candidates.get
      candicate = Random.shuffle(candidates.toSeq).headOption
      extraData = prepareExtra(snap)
    } yield
      BlockHeader(
        parentHash = parent.header.hash,
        ommersHash = ByteVector.empty,
        beneficiary = candicate match {
          case Some((address, auth)) => address.bytes
          case None                  => ByteVector.empty
        },
        nonce = candicate match {
          case Some((address, auth)) => if (auth) Istanbul.nonceAuthVote else Istanbul.nonceDropVote
          case None                  => ByteVector.empty
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
        extraData = extraData,
        mixHash = Istanbul.mixDigest
      )

  private def calcGasLimit(parentGas: BigInt): BigInt = {
    val GasLimitBoundDivisor: Int = 1024
    val gasLimitDifference        = parentGas / GasLimitBoundDivisor
    parentGas + gasLimitDifference - 1
  }

  private def prepareExtra(snapshot: Snapshot): ByteVector = {
    val extra = IstanbulExtra(
      validators = snapshot.getValidators,
      seal = ByteVector.empty,
      committedSeals = List.empty
    )
    return ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra).require.bytes
  }

  override def postProcess(executed: TypedBlock.ExecutedBlock[F]): F[TypedBlock.ExecutedBlock[F]] = F.pure(executed)

  override def mine(executed: TypedBlock.ExecutedBlock[F]): F[TypedBlock.MinedBlock] =
    if (executed.block.header.number == 0) {
      F.raiseError(new Exception("mining the genesis block is not supported"))
    } else {
      for {
        snap <- istanbul.applyHeaders(executed.block.header.number - 1, executed.block.header.parentHash, Nil)
        mined <- if (!snap.validatorSet.contains(istanbul.signer)) {
          F.raiseError(new Exception("unauthorized"))
        } else {
          for {
            sigHash <- F.delay(Istanbul.sigHash(executed.block.header))
            seal    <- istanbul.sign(sigHash)
            extra = IstanbulExtra(snap.getValidators, ByteVector(seal.bytes), List.empty)
            header = Istanbul
              .filteredHeader(executed.block.header, false)
              .copy(extraData = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra).require.bytes)
          } yield MinedBlock(executed.block.copy(header = header), executed.receipts)
        }
      } yield mined
    }

  override def run(block: Block): F[Result] = F.pure(Consensus.Stash(block))

  override def resolveBranch(headers: List[BlockHeader]): F[Consensus.BranchResult] = F.pure(Consensus.NoChainSwitch)
}
