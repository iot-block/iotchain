package jbok.core.mining

import java.io.InputStream
import java.util.Random

import cats.effect.{Bracket, Sync}
import cats.implicits._
import fs2.async.Ref
import jbok.core.configs.MiningConfig
import jbok.core.consensus.{Ethash, ProofOfWork}
import jbok.core.{BlockChain, TxPool}
import better.files._
import cats.data.EitherT
import jbok.core.ledger.OmmersPool
import jbok.core.models.Block
import jbok.core.utils.ByteUtils
import scodec.bits.ByteVector
import jbok.codec._
import jbok.core.api.PublicAPI

sealed trait MiningResult {
  def triedHashes: Int
}
case class MiningSuccessful(triedHashes: Int, pow: ProofOfWork, nonce: ByteVector) extends MiningResult
case class MiningUnsuccessful(triedHashes: Int) extends MiningResult

class Miner[F[_]](
    blockChain: BlockChain[F],
    txPool: TxPool[F],
    ommersPool: OmmersPool[F],
    blockGenerator: BlockGenerator[F],
    miningConfig: MiningConfig,
    publicAPI: PublicAPI[F],
    currentEpoch: Ref[F, Option[Long]],
    currentEpochDagSize: Ref[F, Option[Long]],
    currentEpochDag: Ref[F, Option[Array[Array[Int]]]]
)(implicit F: Sync[F]) {
  private[this] val log = org.log4s.getLogger

  val DagFilePrefix: ByteVector = ByteVector(Array(0xfe, 0xca, 0xdd, 0xba, 0xad, 0xde, 0xe1, 0xfe).map(_.toByte))

  val MaxNonce: BigInt = BigInt(2).pow(64) - 1

  def processMining(): F[Unit] =
    for {
      parentBlock <- blockChain.getBestBlock
      epoch = Ethash.epoch(parentBlock.header.number.toLong + 1)

      currentEpochOpt <- currentEpoch.get
      dagOpt <- currentEpochDag.get
      dagSizeOpt <- currentEpochDagSize.get

      (dag, dagSize) <- (currentEpochOpt, dagOpt, dagSizeOpt) match {
        case (Some(_), Some(dag), Some(dagSize)) =>
          F.pure((dag, dagSize))

        case _ =>
          val seed = Ethash.seed(epoch)
          val dagSize = Ethash.dagSize(epoch)
          val dagNumHashes = (dagSize / Ethash.HASH_BYTES).toInt

          for {
            dag <- if (!dagFile(seed).exists()) {
              F.pure(generateDagAndSaveToFile(epoch, dagNumHashes, seed))
            } else {
              loadDagFromFile(seed, dagNumHashes)
                .getOrElse(generateDagAndSaveToFile(epoch, dagNumHashes, seed))
//                val res = loadDagFromFile(seed, dagNumHashes)
//                res.failed.foreach { ex => log.error(ex, "Cannot read DAG from file") }
//                res.getOrElse(generateDagAndSaveToFile(epoch, dagNumHashes, seed))
            }
            _ <- currentEpoch.setSync(Some(epoch))
            _ <- currentEpochDagSize.setSync(Some(dagSize))
            _ <- currentEpochDag.setSync(Some(dag))
          } yield (dag, dagSize)
      }

      pb <- getBlockForMining(parentBlock)
      _ <- pb match {
        case Left(e) =>
          log.error(s"unable to get block for minig ${e}")
          F.unit

        case Right(PendingBlock(block, _)) =>
          val headerHash = block.header.hashWithoutNonce
          val startTime = System.currentTimeMillis()
          val mineResult = mine(headerHash, block.header.difficulty.toLong, dagSize, dag, miningConfig.mineRounds)
          val time = System.currentTimeMillis() - startTime
          val hashRate = (mineResult.triedHashes * 1000) / time
          publicAPI.submitHashRate(hashRate, ByteVector("jbok-miner".getBytes))
          mineResult match {
            case MiningSuccessful(_, pow, nonce) =>
//              syncController ! RegularSync.MinedBlock(block.copy(header = block.header.copy(nonce = nonce, mixHash = pow.mixHash)))
            case _ => // nothing
          }
          processMining()
      }
    } yield ()

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

  private[jbok] def loadDagFromFile(seed: ByteVector, dagNumHashes: Int): EitherT[F, String, Array[Array[Int]]] =
    EitherT {
      Bracket[F, Throwable]
        .bracket[InputStream, Either[String, Array[Array[Int]]]](F.delay(dagFile(seed).newInputStream)) { in =>
          F.delay {
            val prefix = new Array[Byte](8)
            if (in.read(prefix) != 8 || ByteVector(prefix) != DagFilePrefix) {
              Left("Invalid DAG file prefix")
            } else {
              val buffer = new Array[Byte](64)
              val res = new Array[Array[Int]](dagNumHashes)
              var index = 0

              while (in.read(buffer) > 0) {
                if (index % 100000 == 0)
                  log.info(s"Loading DAG from file ${((index / res.length.toDouble) * 100).toInt}%")
                res(index) = ByteUtils.bytesToInts(buffer)
                index += 1
              }

              if (index == dagNumHashes) {
                Right(res)
              } else {
                Left("DAG file ended unexpectedly")
              }
            }
          }
        } { in =>
          F.delay(in.close())
        }
    }

  private[jbok] def getBlockForMining(parentBlock: Block): F[Either[String, PendingBlock]] =
    for {
      ommers <- getOmmersFromPool(parentBlock.header.number + 1)
      txs <- getTransactionsFromPool
      pb <- blockGenerator
        .generateBlockForMining(parentBlock, txs.map(_.stx), ommers, miningConfig.coinbase)
        .value
        .map {
          case Left(e)   => Left(e)
          case Right(pb) => Right(pb)
        }
    } yield pb

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
        val nonce = (initNonce + n) % MaxNonce
        val nonceBytes = ByteVector(nonce.toUnsignedByteArray).padLeft(8)
        val pow = Ethash.hashimoto(headerHash.toArray, nonceBytes.toArray, dagSize, dag.apply)
        (Ethash.checkDifficulty(difficulty, pow), pow, nonceBytes, n)
      }
      .collectFirst { case (true, pow, nonceBytes, n) => MiningSuccessful(n + 1, pow, nonceBytes) }
      .getOrElse(MiningUnsuccessful(numRounds))
  }

  private[jbok] def getOmmersFromPool(blockNumber: BigInt) =
    ommersPool.getOmmers(blockNumber)

  private[jbok] def getTransactionsFromPool =
    txPool.getPendingTransactions
}
