package jbok.core.consensus.poa.clique

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock._
import jbok.core.models.{Address, Block, BlockHeader, Receipt}
import jbok.core.pool.BlockPool
import jbok.core.pool.BlockPool.Leaf
import scodec.bits.ByteVector

import scala.util.Random
import scala.concurrent.duration._

case class CliqueConsensus[F[_]](
    clique: Clique[F],
    blockPool: BlockPool[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F])
    extends Consensus[F](clique.history, blockPool) {
  private[this] val log = org.log4s.getLogger("CliqueConsensus")

  override def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader]): F[BlockHeader] =
    for {
      parent <- parentOpt.fold(history.getBestBlock)(_.pure[F])
      blockNumber = parent.header.number + 1
      timestamp   = parent.header.unixTimestamp + clique.config.period.toMillis
      snap <- clique.snapshot(blockNumber - 1, parent.header.hash, Nil)
      _ = log.trace(s"loaded snap from block(${blockNumber - 1})")
      _ = log.trace(s"timestamp: ${timestamp}, stime: ${System.currentTimeMillis()}")
    } yield
      BlockHeader(
        parentHash = parent.header.hash,
        ommersHash = ByteVector.empty,
        beneficiary = ByteVector.empty,
        stateRoot = ByteVector.empty,
        //we are not able to calculate transactionsRoot here because we do not know if they will fail
        transactionsRoot = ByteVector.empty,
        receiptsRoot = ByteVector.empty,
        logsBloom = ByteVector.empty,
        difficulty = calcDifficulty(snap, clique.signer, blockNumber),
        number = blockNumber,
        gasLimit = calcGasLimit(parent.header.gasLimit),
        gasUsed = 0,
        unixTimestamp = timestamp,
        extraData = parent.header.extraData,
        mixHash = ByteVector.empty,
        nonce = Clique.nonceDropVote
      )

  override def postProcess(executed: ExecutedBlock[F]): F[ExecutedBlock[F]] =
    F.pure(executed)

  override def mine(executed: ExecutedBlock[F]): F[MinedBlock] = {
    log.trace(s"${clique.signer} start mining ${executed.block.tag}")
    if (executed.block.header.number == 0) {
      F.raiseError(new Exception("mining the genesis block is not supported"))
    } else {
      for {
        snap <- clique.snapshot(executed.block.header.number - 1, executed.block.header.parentHash, Nil)
        mined <- if (!snap.signers.contains(clique.signer)) {
          F.raiseError(new Exception("unauthorized"))
        } else {
          snap.recents.find(_._2 == clique.signer) match {
            case Some((seen, _)) if amongstRecent(executed.block.header.number, seen, snap.signers.size) =>
              // If we're amongst the recent signers, wait for the next block

              val wait = (snap.signers.size / 2 + 1 - (executed.block.header.number - seen).toInt)
                .max(0) * clique.config.period.toMillis
              val delay = 0L.max(executed.block.header.unixTimestamp - System.currentTimeMillis()) + wait
              log.trace(s"signed recently, sleep (${delay}) seconds")

              T.sleep(delay.millis) *>
              F.raiseError(new Exception(
                s"${clique.signer} signed recently, must wait for others: ${executed.block.header.number}, ${seen}, ${snap.signers.size / 2 + 1}, ${snap.recents}"))

            case _ =>
              val wait = 0L.max(executed.block.header.unixTimestamp - System.currentTimeMillis())
              log.trace(s"wait: ${wait}")
              val delay: Long = wait +
                (if (executed.block.header.difficulty == Clique.diffNoTurn) {
                   // It's not our turn explicitly to sign, delay it a bit
                   val wiggle: Long = Random.nextLong().abs % ((snap.signers.size / 2 + 1) * Clique.wiggleTime.toMillis)
                   log.trace(s"${clique.signer} it is not our turn, delay ${wiggle}")
                   wiggle
                 } else {
                   log.trace(s"${clique.signer} it is our turn, mine immediately")
                   0
                 })

              for {
                _ <- T.sleep(delay.millis)
                bytes = Clique.sigHash(executed.block.header)
                signed <- clique.sign(bytes)
                _ = log.trace(s"${clique.signer} mined block(${executed.block.header.number})")
                header = executed.block.header.copy(
                  extraData = executed.block.header.extraData.dropRight(Clique.extraSeal) ++ ByteVector(signed.bytes))
              } yield MinedBlock(executed.block.copy(header = header), executed.receipts)
          }
        }
      } yield mined
    }
  }

  override def run(block: Block): F[Consensus.Result] =
    for {
      best        <- history.getBestBlock
      parentTd    <- history.getTotalDifficultyByHash(best.header.hash).map(_.get)
      snap        <- clique.snapshot(best.header.number, best.header.hash, Nil)
      isDuplicate <- blockPool.isDuplicate(block.header.hash)
      result <- if (isDuplicate) {
        Consensus.Discard(new Exception(s"Duplicated Block: ${block.tag}")).pure[F]
      } else if (block.header.number == best.header.number + 1) {
        for {
          topBlockHash <- blockPool.addBlock(block).map(_.get.hash)
          topBlocks    <- blockPool.getBranch(topBlockHash, delete = true)
        } yield Consensus.Forward(topBlocks)
      } else {
        blockPool.addBlock(block).flatMap[Consensus.Result] {
          case Some(Leaf(leafHash, leafTd)) if leafTd > parentTd =>
            for {
              newBranch <- blockPool.getBranch(leafHash, delete = true)
              staleBlocksWithReceiptsAndTDs <- removeBlocksUntil(newBranch.head.header.parentHash, best.header.number)
                .map(_.reverse)
              staleBlocks = staleBlocksWithReceiptsAndTDs.map(_._1)
              _ <- staleBlocks.traverse(block => blockPool.addBlock(block))
            } yield Consensus.Resolve(staleBlocks, newBranch)

          case _ =>
            F.pure(Consensus.Stash(block))
        }
      }
    } yield result

  override def resolveBranch(headers: List[BlockHeader]): F[Consensus.BranchResult] =
    if (!checkHeaders(headers)) {
      F.pure(Consensus.InvalidBranch)
    } else {
      val parentIsKnown = history.getBlockHeaderByHash(headers.head.parentHash).map(_.isDefined)
      parentIsKnown.ifM(
        ifTrue = {
          // find blocks with same numbers in the current chain, removing any common prefix
          headers.map(_.number).traverse(history.getBlockByNumber).map {
            blocks =>
              val (oldBranch, _) = blocks.flatten
                .zip(headers)
                .dropWhile {
                  case (oldBlock, header) =>
                    oldBlock.header == header
                }
                .unzip
              val newHeaders              = headers.dropWhile(h => oldBranch.headOption.exists(_.header.number > h.number))
              val currentBranchDifficulty = oldBranch.map(_.header.difficulty).sum
              val newBranchDifficulty     = newHeaders.map(_.difficulty).sum
              if (currentBranchDifficulty < newBranchDifficulty) {
                Consensus.NewBetterBranch(oldBranch)
              } else {
                Consensus.NoChainSwitch
              }
          }
        },
        ifFalse = F.pure(Consensus.InvalidBranch)
      )
    }

  //////////////////////////////////
  //////////////////////////////////

  private def removeBlocksUntil(parent: ByteVector, fromNumber: BigInt): F[List[(Block, List[Receipt], BigInt)]] =
    history.getBlockByNumber(fromNumber).flatMap[List[(Block, List[Receipt], BigInt)]] {
      case Some(block) if block.header.hash == parent =>
        F.pure(Nil)

      case Some(block) =>
        for {
          receipts <- history.getReceiptsByHash(block.header.hash).map(_.get)
          td       <- history.getTotalDifficultyByHash(block.header.hash).map(_.get)
          _        <- history.delBlock(block.header.hash, false)
          removed  <- removeBlocksUntil(parent, fromNumber - 1)
        } yield (block, receipts, td) :: removed

      case None =>
        log.error(s"Unexpected missing block number: $fromNumber")
        F.pure(Nil)
    }

  private def checkHeaders(headers: List[BlockHeader]): Boolean =
    if (headers.length > 1)
      headers.zip(headers.tail).forall {
        case (parent, child) =>
          parent.hash == child.parentHash && parent.number + 1 == child.number
      } else
      headers.nonEmpty

  private def calcDifficulty(snapshot: Snapshot, signer: Address, number: BigInt): BigInt =
    if (snapshot.inturn(number, signer)) Clique.diffInTurn else Clique.diffNoTurn

  private def calcGasLimit(parentGas: BigInt): BigInt = {
    val GasLimitBoundDivisor: Int = 1024
    val gasLimitDifference        = parentGas / GasLimitBoundDivisor
    parentGas + gasLimitDifference - 1
  }

  private def amongstRecent(currentNumber: BigInt, seen: BigInt, N: Int): Boolean = {
    val limit = N / 2 + 1
    currentNumber < limit || seen > currentNumber - limit
  }
}
