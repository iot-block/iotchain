package jbok.core.consensus.pow.ethash

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.{BlockChainConfig, MiningConfig, MonetaryPolicyConfig}
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock.MinedBlock
import jbok.core.ledger.{History, TypedBlock}
import jbok.core.models.{Block, BlockHeader}
import jbok.core.pool.BlockPool
import jbok.core.validators.HeaderInvalid.HeaderParentNotFoundInvalid
import jbok.crypto._
import jbok.common._
import scodec.bits.ByteVector

class EthashConsensus[F[_]](
    blockChainConfig: BlockChainConfig,
    miningConfig: MiningConfig,
    history: History[F],
    blockPool: BlockPool[F],
    miner: EthashMiner[F],
    ommersValidator: EthashOmmersValidator[F],
    headerValidator: EthashHeaderValidator[F]
)(implicit F: Sync[F])
    extends Consensus[F](history, blockPool) {

  override def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader]): F[BlockHeader] =
    for {
      parent <- parentOpt.fold(history.getBestBlock)(F.pure)
      number = parent.header.number + 1
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
        extraData = miningConfig.headerExtraData,
        mixHash = ByteVector.empty,
        nonce = ByteVector.empty
      )

  override def postProcess(executed: TypedBlock.ExecutedBlock[F]): F[TypedBlock.ExecutedBlock[F]] = ???

  override def mine(executed: TypedBlock.ExecutedBlock[F]): F[TypedBlock.MinedBlock] =
    miner.mine(executed.block).map(block => MinedBlock(block, executed.receipts))

  override def verify(block: Block): F[Unit] =
    history.getBlockHeaderByHash(block.header.parentHash).flatMap {
      case Some(parent) =>
        headerValidator.validate(parent, block.header) *> ommersValidator.validate(
          block.header.parentHash,
          block.header.number,
          block.body.ommerList,
          getHeaderFromHistoryOrPool,
          getNBlocks
        )

      case None => F.raiseError(HeaderParentNotFoundInvalid)
    }

  override def run(block: Block): F[Consensus.Result] = ???
//    history.getBestBlock.flatMap { parent =>
//      F.ifM(blockPool.isDuplicate(header.hash))(
//        ifTrue = F.pure(Consensus.Discard(new Exception("duplicate"))),
//        ifFalse = for {
//          currentTd <- history.getTotalDifficultyByHash(parent.header.hash).map(_.get)
//          isTopOfChain = header.parentHash == parent.header.hash
//          result = if (isTopOfChain) {
//            Consensus.Forward
//          } else {
//            Consensus.Stash
//          }
//        } yield result
//      )
//    }

  override def resolveBranch(headers: List[BlockHeader]): F[Consensus.BranchResult] =
    checkHeaders(headers).ifM(
      ifTrue = headers
        .map(_.number)
        .traverse(history.getBlockByNumber)
        .map { blocks =>
          val (oldBranch, _) = blocks.flatten
            .zip(headers)
            .dropWhile {
              case (oldBlock, header) =>
                oldBlock.header == header
            }
            .unzip

          val forkNumber = oldBranch.headOption
            .map(_.header.number)
            .getOrElse(headers.head.number)

          val newBranch = headers.filter(_.number >= forkNumber)

          val currentBranchDifficulty = oldBranch.map(_.header.difficulty).sum
          val newBranchDifficulty     = newBranch.map(_.difficulty).sum
          if (currentBranchDifficulty < newBranchDifficulty) {
            Consensus.BetterBranch(NonEmptyList.fromListUnsafe(newBranch))
          } else {
            Consensus.NoChainSwitch
          }
        },
      ifFalse = F.pure(Consensus.InvalidBranch)
    )

  ////////////////////////////////////
  ////////////////////////////////////

  private def getHeaderFromHistoryOrPool(hash: ByteVector): F[Option[BlockHeader]] =
    OptionT(history.getBlockHeaderByHash(hash))
      .orElseF(blockPool.getPooledBlockByHash(hash).map(_.map(_.block.header)))
      .value

  private def getNBlocks(hash: ByteVector, n: Int): F[List[Block]] =
    for {
      pooledBlocks <- blockPool.getBranch(hash, delete = false).map(_.take(n))
      result <- if (pooledBlocks.length == n) {
        pooledBlocks.pure[F]
      } else {
        val chainedBlockHash = pooledBlocks.headOption.map(_.header.parentHash).getOrElse(hash)
        history.getBlockByHash(chainedBlockHash).flatMap {
          case None =>
            F.pure(List.empty[Block])

          case Some(block) =>
            val remaining = n - pooledBlocks.length - 1
            val numbers   = (block.header.number - remaining) until block.header.number
            numbers.toList.map(history.getBlockByNumber).sequence.map(xs => (xs.flatten :+ block) ::: pooledBlocks)
        }
      }
    } yield result

  /**
    * 1. head's parent is known
    * 2. headers form a chain
    */
  private def checkHeaders(headers: List[BlockHeader]): F[Boolean] =
    headers.nonEmpty.pure[F] &&
      history.getBlockHeaderByHash(headers.head.parentHash).map(_.isDefined) &&
      headers
        .zip(headers.tail)
        .forall {
          case (parent, child) =>
            parent.hash == child.parentHash && parent.number + 1 == child.number
        }
        .pure[F]

  private val difficultyCalculator = new EthDifficultyCalculator(blockChainConfig)
  private val rewardCalculator     = new EthRewardCalculator(MonetaryPolicyConfig())

  private def calcDifficulty(blockTime: Long, parentHeader: BlockHeader): F[BigInt] =
    F.pure(difficultyCalculator.calculateDifficulty(blockTime, parentHeader))

  private def calcBlockMinerReward(blockNumber: BigInt, ommersCount: Int): F[BigInt] =
    F.pure(rewardCalculator.calcBlockMinerReward(blockNumber, ommersCount))

  private def calcOmmerMinerReward(blockNumber: BigInt, ommerNumber: BigInt): F[BigInt] =
    F.pure(rewardCalculator.calcOmmerMinerReward(blockNumber, ommerNumber))

  private def getTimestamp: F[Long] =
    F.pure(System.currentTimeMillis())

  private def calcGasLimit(parentGas: BigInt): BigInt = {
    val GasLimitBoundDivisor: Int = 1024
    val gasLimitDifference        = parentGas / GasLimitBoundDivisor
    parentGas + gasLimitDifference - 1
  }
}
