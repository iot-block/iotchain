package jbok.core.consensus.poa.clique

import cats.effect.ConcurrentEffect
import cats.implicits._
import jbok.core.consensus.{Consensus, ConsensusResult}
import jbok.core.models.{Address, Block, BlockHeader}
import jbok.core.pool.BlockPool
import scodec.bits.ByteVector

import scala.util.Random

class CliqueConsensus[F[_]](blockPool: BlockPool[F], clique: Clique[F])(implicit F: ConcurrentEffect[F])
    extends Consensus[F](clique.history, blockPool) {
  private[this] val log = org.log4s.getLogger

  override def semanticValidate(parentHeader: BlockHeader, block: Block): F[Unit] =
    for {
      snap <- clique.snapshot(block.header.number - 1, block.header.hash, Nil)
      _ = if (!snap.inturn(block.header.number, clique.signer))
        F.raiseError(new Exception("invalid turn in block.difficulty"))
      else F.pure(Unit)
    } yield ()

  override def calcDifficulty(blockTime: Long, parentHeader: BlockHeader): F[BigInt] =
    F.pure(BigInt(0))

  override def calcBlockMinerReward(blockNumber: BigInt, ommersCount: Int): F[BigInt] =
    F.pure(BigInt(0))

  override def calcOmmerMinerReward(blockNumber: BigInt, ommerNumber: BigInt): F[BigInt] =
    F.pure(BigInt(0))

  override def getTimestamp: F[Long] =
    F.pure(System.currentTimeMillis())

  override def prepareHeader(parent: Block, ommers: List[BlockHeader]): F[BlockHeader] = {
    val blockNumber = parent.header.number + 1
    val beneficiary = ByteVector.empty
    val timestamp   = parent.header.unixTimestamp + clique.config.period.toMillis
    for {
      snap <- clique.snapshot(blockNumber - 1, parent.header.hash, Nil)
      _ = log.info(s"loaded snap from block(${blockNumber - 1})")
      _ = log.info(s"timestamp: ${timestamp}, stime: ${System.currentTimeMillis()}")
    } yield
      BlockHeader(
        parentHash = parent.header.hash,
        ommersHash = ByteVector.empty,
        beneficiary = beneficiary,
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
  }

  private def calcDifficulty(snapshot: Snapshot, signer: Address, number: BigInt): BigInt =
    if (snapshot.inturn(number, signer)) Clique.diffInTurn else Clique.diffNoTurn

  override def run(parent: Block, current: Block): F[ConsensusResult] =
    for {
      snap <- clique.snapshot(parent.header.number, parent.header.hash, Nil)
      number     = current.header.number
      difficulty = calcDifficulty(snap, Clique.ecrecover(current.header), number)
      isDuplicate <- blockPool.isDuplicate(current.header.hash)
    } yield
      if (isDuplicate) {
        ConsensusResult.BlockInvalid(new Exception(s"Duplicated Block: ${current.tag}"))
      } else if (number == parent.header.number + 1 &&
                 current.header.unixTimestamp == parent.header.unixTimestamp + clique.config.period.toMillis &&
                 current.header.difficulty == difficulty) {
        ConsensusResult.ImportToTop
      } else { ConsensusResult.Pooled }

  override def mine(block: Block): F[Block] = {
    log.info(s"${clique.signer} start mining block(${block.header.number})")
    if (block.header.number == 0) {
      F.raiseError(new Exception("mining the genesis block is not supported"))
    } else {
      for {
        snap <- clique.snapshot(block.header.number - 1, block.header.parentHash, Nil)
        _ = log.info(s"get previous snapshot(${snap.number})")
        mined <- if (!snap.signers.contains(clique.signer)) {
          F.raiseError(new Exception("unauthorized"))
        } else {
          log.info(
            s"${clique.signer} consensus snap.recents: ${snap.recents}, ${snap.recents.find(_._2 == clique.signer)}")
          snap.recents.find(_._2 == clique.signer) match {
            case Some((seen, _)) if amongstRecent(block.header.number, seen, snap.signers.size) =>
              // If we're amongst the recent signers, wait for the next block

              val wait = (snap.signers.size / 2 + 1 - (block.header.number - seen).toInt)
                .max(0) * clique.config.period.toMillis
              val delay = 0L.max(block.header.unixTimestamp - System.currentTimeMillis()) + wait
              log.info(s"signed recently, sleep (${delay}) seconds")
              Thread.sleep(delay)
              F.raiseError(new Exception(
                s"${clique.signer} signed recently, must wait for others: ${block.header.number}, ${seen}, ${snap.signers.size / 2 + 1}, ${snap.recents}"))

            case _ =>
              val wait = 0L.max(block.header.unixTimestamp - System.currentTimeMillis())
              log.info(s"wait: ${wait}")
              val delay: Long = wait +
                (if (block.header.difficulty == Clique.diffNoTurn) {
                   // It's not our turn explicitly to sign, delay it a bit
                   val wiggle: Long = Random.nextLong().abs % ((snap.signers.size / 2 + 1) * Clique.wiggleTime.toMillis)
                   log.info(s"${clique.signer} it is not our turn, delay ${wiggle}")
                   wiggle
                 } else {
                   log.info(s"${clique.signer} it is our turn, mine immediately")
                   0
                 })

              for {
                _ <- F.delay(Thread.sleep(delay))
                bytes = Clique.sigHash(block.header)
                signed <- clique.sign(bytes)
                _ = log.debug(s"${clique.signer} mined block(${block.header.number})")
              } yield
                block.copy(
                  header = block.header.copy(
                    extraData = block.header.extraData.dropRight(Clique.extraSeal) ++ ByteVector(signed.bytes)))
          }
        }
      } yield mined
    }
  }

  private def amongstRecent(currentNumber: BigInt, seen: BigInt, N: Int): Boolean = {
    val limit = N / 2 + 1
    currentNumber < limit || seen > currentNumber - limit
  }
}
