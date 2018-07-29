package jbok.core.ledger

import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
import jbok.core.BlockChain
import jbok.core.models.BlockHeader

class OmmersPool[F[_]: Sync](blockChain: BlockChain[F], ommersList: Ref[F, List[BlockHeader]]) {
  private[this] val log = org.log4s.getLogger

  var ommersPool: Seq[BlockHeader] = Nil
  val ommerGenerationLimit: Int = 6 //Stated on section 11.1, eq. (143) of the YP
  val ommerSizeLimit: Int = 2
  val ommersPoolSize: Int = 2

  def addOmmers(ommers: List[BlockHeader]): F[Unit] = {
    log.info(s"add ${ommers.length} ommers")
    ommersList.modify(xs => (ommers ++ xs).take(ommersPoolSize)).void
  }

  def removeOmmers(ommers: List[BlockHeader]): F[Unit] = {
    val toDelete = ommers.map(_.hash).toSet
    log.info(s"remove ${toDelete.size} ommers")
    ommersList.modify(xs => xs.filter(b => !toDelete.contains(b.hash))).void
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

object OmmersPool {
  def apply[F[_]: Sync](blockChain: BlockChain[F]): F[OmmersPool[F]] =
    for {
      ref <- fs2.async.refOf[F, List[BlockHeader]](Nil)
    } yield new OmmersPool[F](blockChain, ref)
}
