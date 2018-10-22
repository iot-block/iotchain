package jbok.core.pool

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.History
import jbok.core.models.BlockHeader

class OmmerPool[F[_]: Sync](history: History[F], poolSize: Int, ommersList: Ref[F, List[BlockHeader]]) {
  private[this] val log = org.log4s.getLogger

  val ommerGenerationLimit: Int = 6 //Stated on section 11.1, eq. (143) of the YP
  val ommerSizeLimit: Int       = 2

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

object OmmerPool {
  def apply[F[_]: Sync](history: History[F], poolSize: Int = 30): F[OmmerPool[F]] =
    for {
      ref <- Ref.of[F, List[BlockHeader]](Nil)
    } yield new OmmerPool[F](history, poolSize, ref)
}
