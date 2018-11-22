package jbok.core.consensus.poa.clique

import cats.data.NonEmptyList
import cats.effect.ConcurrentEffect
import cats.implicits._
import jbok.core.consensus.Consensus
import jbok.core.ledger.TypedBlock._
import jbok.core.models.{Address, Block, BlockHeader}
import jbok.core.pool.BlockPool
import scodec.bits.ByteVector

import scala.util.Random

case class CliqueConsensus[F[_]](
    clique: Clique[F],
    blockPool: BlockPool[F]
)(implicit F: ConcurrentEffect[F])
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
              Thread.sleep(delay)
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
                _ <- F.delay(Thread.sleep(delay))
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

  override def verifyHeader(header: BlockHeader): F[Consensus.Result] =
    for {
      parent <- history.getBestBlock
      snap   <- clique.snapshot(parent.header.number, parent.header.hash, Nil)
      number     = header.number
      difficulty = calcDifficulty(snap, Clique.ecrecover(header), number)
      isDuplicate <- blockPool.isDuplicate(header.hash)
    } yield
      if (isDuplicate) {
        Consensus.Discard(NonEmptyList.one(new Exception(s"Duplicated Block: ${header.tag}")))
      } else if (number == parent.header.number + 1 &&
                 header.unixTimestamp == parent.header.unixTimestamp + clique.config.period.toMillis &&
                 header.difficulty == difficulty) {
        Consensus.Commit
      } else { Consensus.Stash }

  //////////////////////////////////
  //////////////////////////////////

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
