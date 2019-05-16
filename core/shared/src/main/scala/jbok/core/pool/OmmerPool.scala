package jbok.core.pool

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.config.Configs.OmmerPoolConfig
import jbok.core.models.BlockHeader

final class OmmerPool[F[_]: Sync](config: OmmerPoolConfig) {
  import config._

  val ommersList: Ref[F, List[BlockHeader]] = Ref.unsafe[F, List[BlockHeader]](Nil)

  def addOmmers(ommers: List[BlockHeader]): F[Unit] =
    ommersList.update(xs => (ommers ++ xs).take(poolSize)).void

  def removeOmmers(ommers: List[BlockHeader]): F[Unit] = {
    val toDelete = ommers.map(_.hash).toSet
    ommersList.update(xs => xs.filter(b => !toDelete.contains(b.hash))).void
  }

  def getOmmers(blockNumber: BigInt): F[List[BlockHeader]] =
    for {
      ommers <- ommersList.get
    } yield {
      ommers
        .filter { b =>
          val generationDifference = blockNumber - b.number
          generationDifference > 0 && generationDifference <= ommerGenerationLimit
        }
        .take(ommerSizeLimit)
    }
}
