package jbok.core.consensus.poa.clique

import java.util.concurrent.TimeUnit

import cats.effect.Sync
import cats.implicits._
import jbok.core.History
import jbok.core.consensus.{Consensus, ConsensusResult}
import jbok.core.models.{Block, BlockHeader}
import jbok.crypto.signature.ecdsa.SecP256k1
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration

class CliqueConsensus[F[_]](clique: Clique[F])(implicit F: Sync[F]) extends Consensus[F](clique.history) {
  private[this] val log = org.log4s.getLogger

  override def semanticValidate(parentHeader: BlockHeader, block: Block): F[Unit] =
    F.unit

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
    val timestamp   = System.currentTimeMillis()
    for {
      snap <- clique.snapshot(blockNumber - 1, parent.header.hash, Nil)
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
        difficulty = 0,
        number = blockNumber,
        gasLimit = calcGasLimit(parent.header.gasLimit),
        gasUsed = 0,
        unixTimestamp = timestamp,
        extraData = Clique.fillExtraData(snap.signers.toList),
        mixHash = ByteVector.empty,
        nonce = ByteVector.empty
      )
  }

  override def run(parent: Block, current: Block): F[ConsensusResult] =
    if (current.header.number == parent.header.number + 1) {
      F.pure(ConsensusResult.ImportToTop)
    } else {
      F.pure(ConsensusResult.Pooled)
    }

  override def mine(block: Block): F[Block] =
    if (block.header.number == 0) {
      F.raiseError(new Exception("mining the genesis block is not supported"))
    } else {
      for {
        snap <- clique.snapshot(block.header.number - 1, block.header.parentHash, Nil)
        mined <- if (!snap.signers.contains(clique.signer)) {
          F.raiseError(new Exception("unauthorized"))
        } else {
          snap.recents.find(_._2 == clique.signer) match {
            case Some((seen, _)) if amongstRecent(block.header.number, seen, snap.signers.size / 2 + 1) =>
              // If we're amongst the recent signers, wait for the next block
              F.raiseError(new Exception("signed recently, must wait for others"))

            case _ =>
              val delay: FiniteDuration =
                if (block.header.difficulty == Clique.diffNoTurn) {
                  // It's not our turn explicitly to sign, delay it a bit
                  val wiggle = (snap.signers.size / 2 + 1) * Clique.wiggleTime
                  log.info(s"it is not our turn, delay ${wiggle}")
                  wiggle
                } else {
                  log.info(s"it is our turn, mine immediately")
                  FiniteDuration(0, TimeUnit.MILLISECONDS)
                }

              for {
                _ <- F.delay(Thread.sleep(delay.toMillis))
                bytes  = Clique.sigHash(block.header)
                signed = SecP256k1.sign(bytes.toArray, clique.keyPair).unsafeRunSync()
              } yield
                block.copy(header = block.header.copy(extraData = block.header.extraData ++ ByteVector(signed.bytes)))
          }
        }
      } yield mined
    }

  private def amongstRecent(currentNumber: BigInt, seen: BigInt, N: Int): Boolean = {
    val limit = N / 2 + 1
    currentNumber < limit || seen > currentNumber - limit
  }
}
