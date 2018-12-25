package jbok.core.consensus.poa.clique

import cats.data.NonEmptyList
import cats.effect.{Sync, Timer}
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.common._
import jbok.core.consensus.Consensus
import jbok.core.consensus.poa.clique.Clique.{CliqueAlgo, CliqueExtra}
import jbok.core.ledger.TypedBlock._
import jbok.core.models.{Address, Block, BlockHeader, Receipt}
import jbok.core.pool.BlockPool
import jbok.core.pool.BlockPool.Leaf
import jbok.core.validators.BlockValidator
import jbok.core.validators.HeaderInvalid.HeaderParentNotFoundInvalid
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.util.Random

final class CliqueConsensus[F[_]](clique: Clique[F], pool: BlockPool[F])(
    implicit F: Sync[F],
    T: Timer[F]
) extends Consensus[F](clique.history, pool) {
  private[this] val log = jbok.common.log.getLogger("CliqueConsensus")

  override type Protocol = CliqueAlgo

  override val protocol: Protocol = CliqueAlgo

  override type E = CliqueExtra

  override def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader]): F[BlockHeader] =
    for {
      parent <- parentOpt.fold(history.getBestBlock)(_.pure[F])
      blockNumber = parent.header.number + 1
      timestamp   = parent.header.unixTimestamp + clique.config.period.toMillis
      snap <- clique.applyHeaders(parent.header.number, parent.header.hash, Nil)
    } yield
      BlockHeader(
        parentHash = parent.header.hash,
        ommersHash = Clique.ommersHash,
        beneficiary = ByteVector.empty,
        stateRoot = ByteVector.empty,
        transactionsRoot = ByteVector.empty,
        receiptsRoot = ByteVector.empty,
        logsBloom = ByteVector.empty,
        difficulty = calcDifficulty(snap, clique.signer, blockNumber),
        number = blockNumber,
        gasLimit = calcGasLimit(parent.header.gasLimit),
        gasUsed = 0,
        unixTimestamp = timestamp,
        extra = ByteVector.empty
//        extraData = parent.header.extraData,
//        mixHash = ByteVector.empty,
//        nonce = Clique.nonceDropVote
      )

  override def postProcess(executed: ExecutedBlock[F]): F[ExecutedBlock[F]] =
    F.pure(executed)

  override def mine(executed: ExecutedBlock[F]): F[MinedBlock] = {
    log.trace(s"${clique.signer} start mining ${executed.block.tag}")
    if (executed.block.header.number == 0) {
      F.raiseError(new Exception("mining the genesis block is not supported"))
    } else {
      for {
        snap <- clique.applyHeaders(executed.block.header.number - 1, executed.block.header.parentHash, Nil)
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

              T.sleep(delay.millis) >>
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
                bytes <- Clique.sigHash[F](executed.block.header)
                signed <- clique.sign(bytes)
                _ = log.trace(s"${clique.signer} mined block(${executed.block.header.number})")
                extra = CliqueExtra(Nil, signed)
                extraBytes <- extra.asBytesF[F]
                header = executed.block.header.copy(extra = extraBytes)
              } yield MinedBlock(executed.block.copy(header = header), executed.receipts)
          }
        }
      } yield mined
    }
  }

  override def verify(block: Block): F[Unit] =
    for {
      blockOpt <- pool.getBlockFromPoolOrHistory(block.header.hash)
      _        <- if (blockOpt.isDefined) F.raiseError(new Exception("duplicate block")) else F.unit
      _ <- history.getBlockHeaderByHash(block.header.parentHash).flatMap[Unit] {
        case Some(parent) =>
          val result = for {
//              _ <- check(Set(Clique.diffInTurn, Clique.diffNoTurn).contains(block.header.difficulty), "wrong difficulty")
            _ <- check(calcGasLimit(parent.gasLimit) == block.header.gasLimit, "wrong gasLimit")
            _ <- check(block.header.unixTimestamp == parent.unixTimestamp + clique.config.period.toMillis,
                       "wrong timestamp")
//              _ <- check(block.header.mixHash == ByteVector.empty, "wrong mixHash")
//              _ <- check(Set(Clique.nonceAuthVote, Clique.nonceDropVote).contains(block.header.nonce), "wrong nonce")
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
      bestTd <- history.getTotalDifficultyByHash(best.header.hash).map(_.get)
      result <- if (block.header.number == best.header.number + 1) {
        for {
          topBlockHash <- pool.addBlock(block).map(_.get.hash)
          topBlocks    <- pool.getBranch(topBlockHash, delete = true)
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

          val oldBranch = a.takeWhile(_.isDefined).map(_.get)

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
          receipts <- history.getReceiptsByHash(block.header.hash).map(_.get)
          td       <- history.getTotalDifficultyByHash(block.header.hash).map(_.get)
          _        <- history.delBlock(block.header.hash, false)
          removed  <- removeBlocksUntil(parent, fromNumber - 1)
        } yield (block, receipts, td) :: removed

      case None =>
        log.error(s"Unexpected missing block number: $fromNumber")
        F.pure(Nil)
    }

  /**
    * 1. head's parent is known
    * 2. headers form a chain
    */
  private def checkHeaders(headers: List[BlockHeader]): F[Boolean] =
    headers.nonEmpty.pure[F] &&
      ((headers.head.number == 0).pure[F] || history.getBlockHeaderByHash(headers.head.parentHash).map(_.isDefined)) &&
      headers
        .zip(headers.tail)
        .forall {
          case (parent, child) =>
            parent.hash == child.parentHash && parent.number + 1 == child.number
        }
        .pure[F]

  private def calcDifficulty(snapshot: Snapshot, signer: Address, number: BigInt): BigInt =
    if (snapshot.inturn(number, signer)) Clique.diffInTurn else Clique.diffNoTurn

  private def calcGasLimit(parentGas: BigInt): BigInt =
    parentGas

  private def amongstRecent(currentNumber: BigInt, seen: BigInt, N: Int): Boolean = {
    val limit = N / 2 + 1
    currentNumber < limit || seen > currentNumber - limit
  }

}
//case class CliqueConsensus[F[_]](
//    clique: Clique[F],
//    blockPool: BlockPool[F]
//)(implicit F: ConcurrentEffect[F], T: Timer[F])
//    extends Consensus[F](clique.history, blockPool) {
//  private[this] val log = jbok.common.log.getLogger("CliqueConsensus")
//
//  override def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader]): F[BlockHeader] =
//    for {
//      parent  <- parentOpt.fold(history.getBestBlock)(_.pure[F])
//      blockNumber = parent.header.number + 1
//      timestamp   = parent.header.unixTimestamp + clique.config.period.toMillis
//      snap <- clique.applyHeaders(parent.header.number, parent.header.hash, Nil)
//    } yield
//      BlockHeader(
//        parentHash = parent.header.hash,
//        ommersHash = Clique.ommersHash,
//        beneficiary = ByteVector.empty,
//        stateRoot = ByteVector.empty,
//        transactionsRoot = ByteVector.empty,
//        receiptsRoot = ByteVector.empty,
//        logsBloom = ByteVector.empty,
//        difficulty = calcDifficulty(snap, clique.signer, blockNumber),
//        number = blockNumber,
//        gasLimit = calcGasLimit(parent.header.gasLimit),
//        gasUsed = 0,
//        unixTimestamp = timestamp,
//        extraData = parent.header.extraData,
//        mixHash = ByteVector.empty,
//        nonce = Clique.nonceDropVote
//      )
//
//  override def postProcess(executed: ExecutedBlock[F]): F[ExecutedBlock[F]] =
//    F.pure(executed)
//
//  override def mine(executed: ExecutedBlock[F]): F[MinedBlock] = {
//    log.trace(s"${clique.signer} start mining ${executed.block.tag}")
//    if (executed.block.header.number == 0) {
//      F.raiseError(new Exception("mining the genesis block is not supported"))
//    } else {
//      for {
//        snap <- clique.applyHeaders(executed.block.header.number - 1, executed.block.header.parentHash, Nil)
//        mined <- if (!snap.signers.contains(clique.signer)) {
//          F.raiseError(new Exception("unauthorized"))
//        } else {
//          snap.recents.find(_._2 == clique.signer) match {
//            case Some((seen, _)) if amongstRecent(executed.block.header.number, seen, snap.signers.size) =>
//              // If we're amongst the recent signers, wait for the next block
//              val wait = (snap.signers.size / 2 + 1 - (executed.block.header.number - seen).toInt)
//                .max(0) * clique.config.period.toMillis
//              val delay = 0L.max(executed.block.header.unixTimestamp - System.currentTimeMillis()) + wait
//              log.trace(s"signed recently, sleep (${delay}) seconds")
//
//              T.sleep(delay.millis) >>
//                F.raiseError(new Exception(
//                  s"${clique.signer} signed recently, must wait for others: ${executed.block.header.number}, ${seen}, ${snap.signers.size / 2 + 1}, ${snap.recents}"))
//
//            case _ =>
//              val wait = 0L.max(executed.block.header.unixTimestamp - System.currentTimeMillis())
//              log.trace(s"wait: ${wait}")
//              val delay: Long = wait +
//                (if (executed.block.header.difficulty == Clique.diffNoTurn) {
//                   // It's not our turn explicitly to sign, delay it a bit
//                   val wiggle: Long = Random.nextLong().abs % ((snap.signers.size / 2 + 1) * Clique.wiggleTime.toMillis)
//                   log.trace(s"${clique.signer} it is not our turn, delay ${wiggle}")
//                   wiggle
//                 } else {
//                   log.trace(s"${clique.signer} it is our turn, mine immediately")
//                   0
//                 })
//
//              for {
//                _ <- T.sleep(delay.millis)
//                bytes = Clique.sigHash(executed.block.header)
//                signed <- clique.sign(bytes)
//                _ = log.trace(s"${clique.signer} mined block(${executed.block.header.number})")
//                header = executed.block.header.copy(
//                  extraData = executed.block.header.extraData.dropRight(Clique.extraSeal) ++ ByteVector(signed.bytes))
//              } yield MinedBlock(executed.block.copy(header = header), executed.receipts)
//          }
//        }
//      } yield mined
//    }
//  }
//
//  override def verify(block: Block): F[Unit] =
//    for {
//      blockOpt <- blockPool.getBlockFromPoolOrHistory(block.header.hash)
//      _        <- if (blockOpt.isDefined) F.raiseError(new Exception("duplicate block")) else F.unit
//      _ <- history.getBlockHeaderByHash(block.header.parentHash).flatMap[Unit] {
//        case Some(parent) =>
//          val result = for {
//            _ <- check(Set(Clique.diffInTurn, Clique.diffNoTurn).contains(block.header.difficulty), "wrong difficulty")
//            _ <- check(calcGasLimit(parent.gasLimit) == block.header.gasLimit, "wrong gasLimit")
//            _ <- check(block.header.unixTimestamp == parent.unixTimestamp + clique.config.period.toMillis,
//                       "wrong timestamp")
//            _ <- check(block.header.mixHash == ByteVector.empty, "wrong mixHash")
//            _ <- check(Set(Clique.nonceAuthVote, Clique.nonceDropVote).contains(block.header.nonce), "wrong nonce")
//          } yield ()
//          result match {
//            case Left(e)  => F.raiseError(new Exception(s"block verified invalid because ${e}"))
//            case Right(_) => F.unit
//          }
//
//        case None => F.raiseError(HeaderParentNotFoundInvalid)
//      }
//    } yield ()
//
//  override def run(block: Block): F[Consensus.Result] = {
//    val result: F[Consensus.Result] = for {
//      _ <- verify(block)
//      _ <- history.getBlockHeaderByHash(block.header.parentHash).flatMap {
//        case Some(parent) =>
//          BlockValidator.preExecValidate[F](parent, block) >>
//            clique.applyHeaders(parent.number, parent.hash, List(block.header)).void
//        case None =>
//          F.raiseError[Unit](HeaderParentNotFoundInvalid)
//      }
//      best   <- history.getBestBlock
//      bestTd <- history.getTotalDifficultyByHash(best.header.hash).map(_.get)
//      result <- if (block.header.number == best.header.number + 1) {
//        for {
//          topBlockHash <- blockPool.addBlock(block).map(_.get.hash)
//          topBlocks    <- blockPool.getBranch(topBlockHash, delete = true)
//        } yield Consensus.Forward(topBlocks)
//      } else {
//        blockPool.addBlock(block).flatMap[Consensus.Result] {
//          case Some(Leaf(leafHash, leafTd)) if leafTd > bestTd =>
//            for {
//              newBranch <- blockPool.getBranch(leafHash, delete = true)
//              staleBlocksWithReceiptsAndTDs <- removeBlocksUntil(newBranch.head.header.parentHash, best.header.number)
//                .map(_.reverse)
//              staleBlocks = staleBlocksWithReceiptsAndTDs.map(_._1)
//              _ <- staleBlocks.traverse(block => blockPool.addBlock(block))
//            } yield Consensus.Fork(staleBlocks, newBranch)
//
//          case _ =>
//            F.pure(Consensus.Stash(block))
//        }
//      }
//    } yield result
//
//    result.attempt.map {
//      case Left(e)  => Consensus.Discard(e)
//      case Right(x) => x
//    }
//  }
//
//  override def resolveBranch(headers: List[BlockHeader]): F[Consensus.BranchResult] =
//    checkHeaders(headers).ifM(
//      ifTrue = headers
//        .map(_.number)
//        .traverse(history.getBlockByNumber)
//        .map { blocks =>
//          val (a, newBranch) = blocks
//            .zip(headers)
//            .dropWhile {
//              case (Some(block), header) if block.header == header => true
//              case _                                               => false
//            }
//            .unzip
//
//          val oldBranch = a.takeWhile(_.isDefined).map(_.get)
//
//          val currentBranchDifficulty = oldBranch.map(_.header.difficulty).sum
//          val newBranchDifficulty     = newBranch.map(_.difficulty).sum
//          if (currentBranchDifficulty < newBranchDifficulty) {
//            Consensus.BetterBranch(NonEmptyList.fromListUnsafe(newBranch))
//          } else {
//            Consensus.NoChainSwitch
//          }
//        },
//      ifFalse = F.pure(Consensus.InvalidBranch)
//    )
//
//  //////////////////////////////////
//  //////////////////////////////////
//
//  private def check(b: Boolean, message: String): Either[String, Unit] =
//    if (b) {
//      Right(())
//    } else {
//      Left(message)
//    }
//
//  private def removeBlocksUntil(parent: ByteVector, fromNumber: BigInt): F[List[(Block, List[Receipt], BigInt)]] =
//    history.getBlockByNumber(fromNumber).flatMap[List[(Block, List[Receipt], BigInt)]] {
//      case Some(block) if block.header.hash == parent =>
//        F.pure(Nil)
//
//      case Some(block) =>
//        for {
//          receipts <- history.getReceiptsByHash(block.header.hash).map(_.get)
//          td       <- history.getTotalDifficultyByHash(block.header.hash).map(_.get)
//          _        <- history.delBlock(block.header.hash, false)
//          removed  <- removeBlocksUntil(parent, fromNumber - 1)
//        } yield (block, receipts, td) :: removed
//
//      case None =>
//        log.error(s"Unexpected missing block number: $fromNumber")
//        F.pure(Nil)
//    }
//
//  /**
//    * 1. head's parent is known
//    * 2. headers form a chain
//    */
//  private def checkHeaders(headers: List[BlockHeader]): F[Boolean] =
//    headers.nonEmpty.pure[F] &&
//      ((headers.head.number == 0).pure[F] || history.getBlockHeaderByHash(headers.head.parentHash).map(_.isDefined)) &&
//      headers
//        .zip(headers.tail)
//        .forall {
//          case (parent, child) =>
//            parent.hash == child.parentHash && parent.number + 1 == child.number
//        }
//        .pure[F]
//
//  private def calcDifficulty(snapshot: Snapshot, signer: Address, number: BigInt): BigInt =
//    if (snapshot.inturn(number, signer)) Clique.diffInTurn else Clique.diffNoTurn
//
//  private def calcGasLimit(parentGas: BigInt): BigInt =
//    parentGas
//
//  private def amongstRecent(currentNumber: BigInt, seen: BigInt, N: Int): Boolean = {
//    val limit = N / 2 + 1
//    currentNumber < limit || seen > currentNumber - limit
//  }
//}
