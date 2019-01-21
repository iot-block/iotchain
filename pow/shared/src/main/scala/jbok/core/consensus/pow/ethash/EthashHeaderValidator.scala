package jbok.core.consensus.pow.ethash

import cats.effect.Effect
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.HistoryConfig
import jbok.core.consensus.pow.ethash.Ethash.EthashExtra
import jbok.core.consensus.pow.ethash.EthashHeaderInvalid._
import jbok.core.models.BlockHeader
import jbok.crypto._
import scodec.bits.ByteVector

object EthashHeaderInvalid {
  case object HeaderExtraDataInvalid    extends Exception("HeaderExtraDataInvalid")
  case object DaoHeaderExtraDataInvalid extends Exception("DaoHeaderExtraDataInvalid")
  case object HeaderDifficultyInvalid   extends Exception("HeaderDifficultyInvalid")
  case object HeaderPoWInvalid          extends Exception("HeaderPoWInvalid")
}

class EthashHeaderValidator[F[_]](blockChainConfig: HistoryConfig)(implicit F: Effect[F]) {
  private val MaxPowCaches: Int     = 2

  // for validate pow
  class PowCacheData(val cache: Array[Int], val dagSize: Long)
  lazy val epoch0PowCache                          = new PowCacheData(cache = Ethash.makeCache(0), dagSize = Ethash.dagSize(0))
  val powCaches: java.util.Map[Long, PowCacheData] = new java.util.concurrent.ConcurrentHashMap[Long, PowCacheData]()

  private def validateDifficulty(
      difficulty: BigInt,
      timestamp: Long,
      number: BigInt,
      parentHeader: BlockHeader
  ): F[BigInt] = {
    val diffCal = new EthDifficultyCalculator(blockChainConfig)
    if (difficulty == diffCal.calculateDifficulty(timestamp, parentHeader)) F.pure(difficulty)
    else F.raiseError(HeaderDifficultyInvalid)
  }

  private def validatePow(header: BlockHeader): F[BlockHeader] = {
    import scala.collection.JavaConverters._
    def getPowCacheData(epoch: Long): PowCacheData =
      if (epoch == 0) epoch0PowCache
      else
        Option(powCaches.get(epoch)) match {
          case Some(pcd) => pcd
          case None =>
            val data = new PowCacheData(cache = Ethash.makeCache(epoch), dagSize = Ethash.dagSize(epoch))

            val keys         = powCaches.keySet().asScala
            val keysToRemove = keys.toSeq.sorted.take(keys.size - MaxPowCaches + 1)
            keysToRemove.foreach(powCaches.remove)
            powCaches.put(epoch, data)
            data
        }

    val powCacheData = getPowCacheData(Ethash.epoch(header.number.toLong))

    val extra            = RlpCodec.decode[EthashExtra](header.extra.bits).require.value
    val hashWithoutNonce = header.copy(extra = ByteVector.empty).asValidBytes.kec256

    val proofOfWork = {
      Ethash.hashimotoLight(
        hashWithoutNonce.toArray,
        extra.nonce.toArray,
        powCacheData.dagSize,
        powCacheData.cache
      )
    }

    if (proofOfWork.mixHash == extra.mixHash && Ethash.checkDifficulty(header.difficulty.toLong, proofOfWork)) {
      F.pure(header)
    } else {
      F.raiseError(HeaderPoWInvalid)
    }
  }

  def validate(parentHeader: BlockHeader, header: BlockHeader): F[Unit] =
    for {
      _ <- validateDifficulty(header.difficulty, header.unixTimestamp, header.number, parentHeader)
      _ <- validatePow(header)
    } yield ()
}
