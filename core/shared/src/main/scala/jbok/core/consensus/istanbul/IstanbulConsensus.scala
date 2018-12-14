package jbok.core.consensus.istanbul

import cats.effect.ConcurrentEffect
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.consensus.{Consensus, ConsensusResult}
import jbok.core.models.{Address, Block, BlockHeader}
import jbok.core.pool.BlockPool
import scodec.bits.ByteVector
import jbok.core.consensus.istanbul.IstanbulInvalid._
import cats.implicits._
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

class IstanbulConsensus[F[_]](blockPool: BlockPool[F], istanbul: Istanbul[F])(implicit F: ConcurrentEffect[F])
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
          snap      <- istanbul.snapshot(header.number - 1, parentHeader.hash, Nil)
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
        .map(seal =>
          F.fromOption(Signature[ECDSA].recoverPublic(proposalSeal.kec256.toArray, CryptoSignature(seal.toArray), None),
                       CommittedSealInvalid))
        .sequence
      validSeals = validSealAddrs.filter(pk => snapshot.validatorSet.contains(Address(pk.bytes.kec256))).distinct
      _ <- if (validSeals.size <= 2 * snapshot.f) F.raiseError(CommittedSealInvalid) else F.unit
    } yield ()

  private def prepareCommittedSeal(hash: ByteVector): ByteVector =
    hash ++ ByteVector(Message.msgCommitCode)

  private def validateSigner(header: BlockHeader, snapshot: Snapshot): F[Unit] =
    for {
      _      <- if (header.number == 0) F.raiseError(BlockNumberInvalid) else F.unit
      signer <- F.delay(Istanbul.ecrecover(header))
      _      <- if (snapshot.validatorSet.contains(signer)) F.unit else F.raiseError(SignerUnauthorizedInvalid)
    } yield ()

  override def semanticValidate(parentHeader: BlockHeader, block: Block): F[Unit] =
    validateHeader(parentHeader, block.header)

  override def calcDifficulty(blockTime: Long, parentHeader: BlockHeader): F[BigInt] =
    F.pure(istanbul.config.defaultDifficulty)

  override def calcBlockMinerReward(blockNumber: BigInt, ommersCount: Int): F[BigInt] = F.pure(BigInt(0))

  override def calcOmmerMinerReward(blockNumber: BigInt, ommerNumber: BigInt): F[BigInt] = F.pure(BigInt(0))

  override def getTimestamp: F[Long] = F.pure(System.currentTimeMillis())

  override def prepareHeader(parent: Block, ommers: List[BlockHeader]): F[BlockHeader] = {
    val blockNumber = parent.header.number + 1
    val timestamp   = parent.header.unixTimestamp + istanbul.config.period.toMillis
    for {
      snap       <- istanbul.snapshot(blockNumber - 1, parent.header.hash, Nil)
      difficulty <- calcDifficulty(timestamp, parent.header)
      candicate = Random.shuffle(istanbul.candidates.toSeq).headOption
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
  }

  private def prepareExtra(snapshot: Snapshot): ByteVector = {
    val extra = IstanbulExtra(
      validators = snapshot.getValidators,
      seal = ByteVector.empty,
      committedSeals = List.empty
    )
    return ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra).require.bytes
  }

  override def run(parent: Block, current: Block): F[ConsensusResult] = ???

  override def mine(block: Block): F[Block] = ???
}
