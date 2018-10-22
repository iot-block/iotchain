package jbok.core.consensus.pow.ethash

import better.files.File
import cats.effect.Effect
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.codec._
import jbok.core.config.Configs.MiningConfig
import jbok.core.consensus.pow.ProofOfWork
import jbok.core.models.Block
import jbok.core.utils.ByteUtils
import scodec.bits.ByteVector

import scala.util.Random

sealed trait MiningResult {
  def triedHashes: Int
}
case class MiningSuccessful(triedHashes: Int, pow: ProofOfWork, nonce: ByteVector) extends MiningResult
case class MiningUnsuccessful(triedHashes: Int)                                    extends MiningResult

class EthashMinerPlatform[F[_]](
    miningConfig: MiningConfig,
    currentEpoch: Ref[F, Option[Long]],
    currentEpochDagSize: Ref[F, Option[Long]],
    currentEpochDag: Ref[F, Option[Array[Array[Int]]]]
)(implicit F: Effect[F])
    extends EthashMiner[F] {
  private[this] val log = org.log4s.getLogger

  override def mine(block: Block): F[Block] = {
    val epoch = Ethash.epoch(block.header.number.toLong)
    for {
      currentEpochOpt <- currentEpoch.get
      dagOpt          <- currentEpochDag.get
      dagSizeOpt      <- currentEpochDagSize.get

      (dag, dagSize) <- (currentEpochOpt, dagOpt, dagSizeOpt) match {
        case (Some(_), Some(dag), Some(dagSize)) =>
          F.pure((dag, dagSize))

        case _ =>
          val seed         = Ethash.seed(epoch)
          val dagSize      = Ethash.dagSize(epoch)
          val dagNumHashes = (dagSize / Ethash.HASH_BYTES).toInt

          for {
            dag <- if (!dagFile(seed).exists()) {
              F.pure(generateDagAndSaveToFile(epoch, dagNumHashes, seed))
            } else {
              loadDagFromFile(seed, dagNumHashes).attemptT
                .getOrElse(generateDagAndSaveToFile(epoch, dagNumHashes, seed))
            }
            _ <- currentEpoch.set(Some(epoch))
            _ <- currentEpochDagSize.set(Some(dagSize))
            _ <- currentEpochDag.set(Some(dag))
          } yield (dag, dagSize)
      }
    } yield {
      val headerHash = block.header.hashWithoutNonce
      val startTime  = System.currentTimeMillis()
      val mineResult = mine(headerHash, block.header.difficulty.toLong, dagSize, dag, miningConfig.mineRounds)
      val time       = System.currentTimeMillis() - startTime
      val hashRate   = (mineResult.triedHashes * 1000) / time
      mineResult match {
        case MiningSuccessful(_, pow, nonce) =>
          block.copy(header = block.header.copy(nonce = nonce, mixHash = pow.mixHash))
        case MiningUnsuccessful(tried) =>
          throw new Exception(s"mining unsuccessful, tried ${tried}")
      }
    }
  }

  ///////////////////////////////////
  ///////////////////////////////////

  private val DagFilePrefix: ByteVector = ByteVector(
    Array(0xfe, 0xca, 0xdd, 0xba, 0xad, 0xde, 0xe1, 0xfe).map(_.toByte))

  private val MaxNonce: BigInt = BigInt(2).pow(64) - 1

  private[jbok] def mine(
      headerHash: ByteVector,
      difficulty: Long,
      dagSize: Long,
      dag: Array[Array[Int]],
      numRounds: Int
  ): MiningResult = {
    val initNonce = BigInt(64, new Random())

    (0 to numRounds).toStream
      .map { n =>
        val nonce      = (initNonce + n) % MaxNonce
        val nonceBytes = ByteVector(nonce.toUnsignedByteArray).padLeft(8)
        val pow        = Ethash.hashimoto(headerHash.toArray, nonceBytes.toArray, dagSize, dag.apply)
        (Ethash.checkDifficulty(difficulty, pow), pow, nonceBytes, n)
      }
      .collectFirst { case (true, pow, nonceBytes, n) => MiningSuccessful(n + 1, pow, nonceBytes) }
      .getOrElse(MiningUnsuccessful(numRounds))
  }

  private[jbok] def dagFile(seed: ByteVector): File =
    File(s"${miningConfig.ethashDir}/full-R${Ethash.Revision}-${seed.take(8).toHex}")

  private[jbok] def generateDagAndSaveToFile(epoch: Long, dagNumHashes: Int, seed: ByteVector): Array[Array[Int]] = {
    val file = dagFile(seed)
    if (file.exists()) file.delete()
    file.parent.createIfNotExists(asDirectory = true, createParents = true)

    val res = new Array[Array[Int]](dagNumHashes)
    val outputStream = dagFile(seed).fileOutputStream().map { out =>
      out.write(DagFilePrefix.toArray)
      val cache = Ethash.makeCache(epoch)
      (0 until dagNumHashes).foreach { i =>
        val item = Ethash.calcDatasetItem(cache, i)
        out.write(ByteUtils.intsToBytes(item))
        res(i) = item

        if (i % 100000 == 0) log.info(s"Generating DAG ${((i / dagNumHashes.toDouble) * 100).toInt}%")
      }
    }
    res
  }

  private[jbok] def loadDagFromFile(seed: ByteVector, dagNumHashes: Int): F[Array[Array[Int]]] = ???
//    Bracket[F, Throwable]
//      .bracket[InputStream, Array[Array[Int]]](F.delay(dagFile(seed).newInputStream)) { in =>
//        val prefix = new Array[Byte](8)
//        if (in.read(prefix) != 8 || ByteVector(prefix) != DagFilePrefix) {
//          F.raiseError(new Exception("Invalid DAG file prefix"))
//        } else {
//          val buffer = new Array[Byte](64)
//          val res    = new Array[Array[Int]](dagNumHashes)
//          var index  = 0
//
//          while (in.read(buffer) > 0) {
//            if (index % 100000 == 0)
//              log.info(s"Loading DAG from file ${((index / res.length.toDouble) * 100).toInt}%")
//            res(index) = ByteUtils.bytesToInts(buffer)
//            index += 1
//          }
//
//          if (index == dagNumHashes) {
//            F.pure(res)
//          } else {
//            F.raiseError(new Exception("DAG file ended unexpectedly"))
//          }
//        }
//      } { in =>
//        F.delay(in.close())
//      }
}

object EthashMinerPlatform {
  def apply[F[_]: Effect](
      miningConfig: MiningConfig
  ): F[EthashMiner[F]] =
    for {
      currentEpoch        <- Ref.of[F, Option[Long]](None)
      currentEpochDagSize <- Ref.of[F, Option[Long]](None)
      currentEpochDag     <- Ref.of[F, Option[Array[Array[Int]]]](None)
    } yield
      new EthashMinerPlatform[F](
        miningConfig,
        currentEpoch,
        currentEpochDagSize,
        currentEpochDag
      )
}
