package jbok.core.consensus.pow.ethash

import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.core.History
import jbok.core.config.Configs.{BlockChainConfig, MiningConfig, MonetaryPolicyConfig}
import jbok.core.consensus.{Consensus, ConsensusResult}
import jbok.core.models.{Block, BlockHeader}
import jbok.core.pool.BlockPool
import jbok.crypto._
import scodec.bits.ByteVector

class EthashConsensus[F[_]](
    blockChainConfig: BlockChainConfig,
    miningConfig: MiningConfig,
    history: History[F],
    blockPool: BlockPool[F],
    miner: EthashMiner[F],
    ommersValidator: EthashOmmersValidator[F],
    headerValidator: EthashHeaderValidator[F]
)(implicit F: Sync[F]) extends Consensus[F](history, blockPool) {
  val difficultyCalculator = new EthDifficultyCalculator(blockChainConfig)
  val rewardCalculator     = new EthRewardCalculator(MonetaryPolicyConfig())

  override def semanticValidate(parentHeader: BlockHeader, block: Block): F[Unit] =
    for {
      _ <- headerValidator.validate(parentHeader, block.header)
      _ <- ommersValidator.validate(
        block.header.parentHash,
        block.header.number,
        block.body.uncleNodesList,
        blockPool.getHeader,
        blockPool.getNBlocks
      )
    } yield ()

  override def calcDifficulty(blockTime: Long, parentHeader: BlockHeader): F[BigInt] =
    F.pure(difficultyCalculator.calculateDifficulty(blockTime, parentHeader))

  override def calcBlockMinerReward(blockNumber: BigInt, ommersCount: Int): F[BigInt] =
    F.pure(rewardCalculator.calcBlockMinerReward(blockNumber, ommersCount))

  override def calcOmmerMinerReward(blockNumber: BigInt, ommerNumber: BigInt): F[BigInt] =
    F.pure(rewardCalculator.calcOmmerMinerReward(blockNumber, ommerNumber))

  override def getTimestamp: F[Long] =
    F.pure(System.currentTimeMillis())

  override def prepareHeader(parent: Block, ommers: List[BlockHeader]): F[BlockHeader] = {
    val number = parent.header.number + 1
    for {
      timestamp  <- getTimestamp
      difficulty <- calcDifficulty(timestamp, parent.header)
    } yield
      BlockHeader(
        parentHash = parent.header.hash,
        ommersHash = RlpCodec.encode(ommers).require.bytes.kec256,
        beneficiary = miningConfig.coinbase.bytes,
        stateRoot = ByteVector.empty,
        //we are not able to calculate transactionsRoot here because we do not know if they will fail
        transactionsRoot = ByteVector.empty,
        receiptsRoot = ByteVector.empty,
        logsBloom = ByteVector.empty,
        difficulty = difficulty,
        number = number,
        gasLimit = calcGasLimit(parent.header.gasLimit),
        gasUsed = 0,
        unixTimestamp = timestamp,
        extraData = blockChainConfig.daoForkConfig
          .flatMap(daoForkConfig => daoForkConfig.getExtraData(number))
          .getOrElse(miningConfig.headerExtraData),
        mixHash = ByteVector.empty,
        nonce = ByteVector.empty
      )
  }

  override def run(parent: Block, current: Block): F[ConsensusResult] =
    F.ifM(blockPool.isDuplicate(current.header.hash))(
      ifTrue = F.pure(ConsensusResult.BlockInvalid(new Exception("duplicate"))),
      ifFalse = for {
        currentTd <- history.getTotalDifficultyByHash(parent.header.hash).map(_.get)
        isTopOfChain = current.header.parentHash == parent.header.hash
        result = if (isTopOfChain) {
          ConsensusResult.ImportToTop
        } else {
          ConsensusResult.Pooled
        }
      } yield result
    )

  override def mine(block: Block): F[Block] = miner.mine(block)
}
