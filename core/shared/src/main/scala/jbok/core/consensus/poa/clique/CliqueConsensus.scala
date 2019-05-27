package jbok.core.consensus.poa.clique

import cats.data.NonEmptyList
import cats.effect.{Sync, Timer}
import cats.implicits._
import jbok.common._
import jbok.common.log.Logger
import jbok.core.config.MiningConfig
import jbok.core.consensus.Consensus
import jbok.core.ledger.History
import jbok.core.ledger.TypedBlock._
import jbok.core.models.{Address, Block, BlockHeader, Receipt}
import jbok.core.pool.BlockPool
import jbok.core.pool.BlockPool.Leaf
import jbok.core.validators.BlockValidator
import jbok.core.validators.HeaderInvalid.HeaderParentNotFoundInvalid
import jbok.persistent.DBErr
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.util.Random

final class CliqueConsensus[F[_]](config: MiningConfig, history: History[F], clique: Clique[F], pool: BlockPool[F])(
    implicit F: Sync[F],
    T: Timer[F]
) extends Consensus[F] {
  private[this] val log = Logger[F]

  override def prepareHeader(parentOpt: Option[Block]): F[BlockHeader] =
    for {
      parent <- parentOpt.fold(history.getBestBlock)(_.pure[F])
      blockNumber = parent.header.number + 1
      timestamp   = parent.header.unixTimestamp + config.period.toMillis
      snap <- clique.applyHeaders(parent.header.number, parent.header.hash, Nil)
      _    <- clique.clearProposalIfMine(parent.header)
    } yield
      BlockHeader(
        parentHash = parent.header.hash,
        beneficiary = ByteVector.empty,
        stateRoot = ByteVector.empty,
        transactionsRoot = ByteVector.empty,
        receiptsRoot = ByteVector.empty,
        logsBloom = ByteVector.empty,
        difficulty = calcDifficulty(snap, clique.minerAddress, blockNumber),
        number = blockNumber,
        gasLimit = calcGasLimit(parent.header.gasLimit),
        gasUsed = 0,
        unixTimestamp = timestamp,
        extra = ByteVector.empty
      )

  override def postProcess(executed: ExecutedBlock[F]): F[ExecutedBlock[F]] =
    F.pure(executed)

  override def mine(executed: ExecutedBlock[F]): F[MinedBlock] =
    if (executed.block.header.number == 0) {
      F.raiseError(new Exception("mining the genesis block is not supported"))
    } else {
      for {
        snap <- clique.applyHeaders(executed.block.header.number - 1, executed.block.header.parentHash, Nil)
        mined <- if (!snap.miners.contains(clique.minerAddress)) {
          F.raiseError(new Exception(s"unauthorized miner ${clique.minerAddress}"))
        } else {
          snap.recents.find(_._2 == clique.minerAddress) match {
            case Some((seen, _)) if amongstRecent(executed.block.header.number, seen, snap.miners.size) =>
              // If we're amongst the recent miners, wait for the next block
              val wait = (snap.miners.size / 2 + 1 - (executed.block.header.number - seen).toInt)
                .max(0) * config.period.toMillis
              val delay = 0L.max(executed.block.header.unixTimestamp - System.currentTimeMillis()) + wait

              log.i(s"mined recently, sleep (${delay}) millis") >> T.sleep(delay.millis) >> mine(executed)

            case _ =>
              val wait = 0L.max(executed.block.header.unixTimestamp - System.currentTimeMillis())
              for {
                delay <- if (executed.block.header.difficulty == Clique.diffNoTurn) {
                  // It's not our turn explicitly to sign, delay it a bit
                  val wiggle = Random.nextLong().abs % ((snap.miners.size / 2 + 1) * Clique.wiggleTime.toMillis)
                  log.trace(s"${clique.minerAddress} it is not our turn, delay ${wiggle}").as(wait + wiggle)
                } else {
                  log.trace(s"${clique.minerAddress} it is our turn, mine immediately").as(wait)
                }
                _      <- T.sleep(delay.millis)
                _      <- log.trace(s"${clique.minerAddress} mined block(${executed.block.header.number})")
                header <- clique.fillExtraData(executed.block.header)
              } yield MinedBlock(executed.block.copy(header = header), executed.receipts)
          }
        }
      } yield mined
    }

  override def verify(block: Block): F[Unit] =
    for {
      blockOpt <- pool.getBlockFromPoolOrHistory(block.header.hash)
      _        <- if (blockOpt.isDefined) F.raiseError(new Exception("duplicate block")) else F.unit
      _ <- history.getBlockHeaderByHash(block.header.parentHash).flatMap[Unit] {
        case Some(parent) =>
          val result = for {
            _ <- check(calcGasLimit(parent.gasLimit) == block.header.gasLimit, "wrong gasLimit")
            _ <- check(block.header.unixTimestamp == parent.unixTimestamp + config.period.toMillis, "wrong timestamp")
          } yield ()
          result match {
            case Left(e)  => F.raiseError(new Exception(s"block verified invalid because ${e}"))
            case Right(_) => F.unit
          }

        case None => F.raiseError(HeaderParentNotFoundInvalid)
      }
    } yield ()

  override def run(block: Block): F[Consensus.Result] = {
    val result: F[Consensus.Result] = for {
      _ <- verify(block)
      _ <- history.getBlockHeaderByHash(block.header.parentHash).flatMap {
        case Some(parent) =>
          BlockValidator.preExecValidate[F](parent, block) >>
            clique.applyHeaders(parent.number, parent.hash, List(block.header)).void
        case None =>
          F.raiseError[Unit](HeaderParentNotFoundInvalid)
      }
      best   <- history.getBestBlock
      bestTd <- history.getTotalDifficultyByHash(best.header.hash).flatMap(opt => F.fromOption(opt, DBErr.NotFound))
      result <- if (block.header.number == best.header.number + 1) {
        for {
          topBlockHash <- pool.addBlock(block).map {
            case Some(leaf) => leaf.hash
            case None       => ???
          }
          topBlocks <- pool.getBranch(topBlockHash, delete = true)
        } yield Consensus.Forward(topBlocks)
      } else {
        pool.addBlock(block).flatMap[Consensus.Result] {
          case Some(Leaf(leafHash, leafTd)) if leafTd > bestTd =>
            for {
              newBranch <- pool.getBranch(leafHash, delete = true)
              staleBlocksWithReceiptsAndTDs <- removeBlocksUntil(newBranch.head.header.parentHash, best.header.number)
                .map(_.reverse)
              staleBlocks = staleBlocksWithReceiptsAndTDs.map(_._1)
              _ <- staleBlocks.traverse(block => pool.addBlock(block))
            } yield Consensus.Fork(staleBlocks, newBranch)

          case _ =>
            F.pure(Consensus.Stash(block))
        }
      }
    } yield result

    result.attempt.map {
      case Left(e)  => Consensus.Discard(e)
      case Right(x) => x
    }
  }

  override def resolveBranch(headers: List[BlockHeader]): F[Consensus.BranchResult] =
    checkHeaders(headers).ifM(
      ifTrue = headers
        .map(_.number)
        .traverse(history.getBlockByNumber)
        .map { blocks =>
          val (a, newBranch) = blocks
            .zip(headers)
            .dropWhile {
              case (Some(block), header) if block.header == header => true
              case _                                               => false
            }
            .unzip

          val oldBranch = a.takeWhile(_.isDefined).collect { case Some(b) => b }

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

  ///////////////////////////////////
  ///////////////////////////////////

  private def check(b: Boolean, message: String): Either[String, Unit] =
    if (b) {
      Right(())
    } else {
      Left(message)
    }

  private def removeBlocksUntil(parent: ByteVector, fromNumber: BigInt): F[List[(Block, List[Receipt], BigInt)]] =
    history.getBlockByNumber(fromNumber).flatMap[List[(Block, List[Receipt], BigInt)]] {
      case Some(block) if block.header.hash == parent =>
        F.pure(Nil)

      case Some(block) =>
        for {
          receipts <- history.getReceiptsByHash(block.header.hash).flatMap(opt => F.fromOption(opt, DBErr.NotFound))
          td       <- history.getTotalDifficultyByHash(block.header.hash).flatMap(opt => F.fromOption(opt, DBErr.NotFound))
          _        <- history.delBlock(block.header.hash)
          removed  <- removeBlocksUntil(parent, fromNumber - 1)
        } yield (block, receipts, td) :: removed

      case None =>
        log.error(s"Unexpected missing block number: $fromNumber").as(Nil)
    }

  /**
    * 1. head's parent is known
    * 2. headers form a chain
    */
  private def checkHeaders(headers: List[BlockHeader]): F[Boolean] =
    headers match {
      case head :: tail =>
        ((head.number == 0).pure[F] || history.getBlockHeaderByHash(head.parentHash).map(_.isDefined)) &&
          headers
            .zip(tail)
            .forall {
              case (parent, child) =>
                parent.hash == child.parentHash && parent.number + 1 == child.number
            }
            .pure[F]

      case Nil => F.pure(false)
    }

  private def calcDifficulty(snapshot: Snapshot, miner: Address, number: BigInt): BigInt =
    if (snapshot.inturn(number, miner)) Clique.diffInTurn else Clique.diffNoTurn

  private def calcGasLimit(parentGas: BigInt): BigInt =
    parentGas

  private def amongstRecent(currentNumber: BigInt, seen: BigInt, N: Int): Boolean = {
    val limit = N / 2 + 1
    currentNumber < limit || seen > currentNumber - limit
  }
}
