package jbok.core.store
import cats.effect.Sync
import jbok.codec.rlp.implicits._
import jbok.persistent.KeyValueDB

final class AppStateStore[F[_]: Sync](db: KeyValueDB[F]) {
  val ns                    = namespaces.AppStateNamespace
  val BestBlockNumber       = "BestBlockNumber"
  val FastSyncDone          = "FastSyncDone"
  val EstimatedHighestBlock = "EstimatedHighestBlock"
  val SyncStartingBlock     = "SyncStartingBlock"

  def getBestBlockNumber: F[BigInt] =
    db.getOptT[String, BigInt](BestBlockNumber, ns).getOrElse(BigInt(0))

  def putBestBlockNumber(bestBlockNumber: BigInt): F[Unit] =
    db.put[String, BigInt](BestBlockNumber, bestBlockNumber, ns)

  def getFastSyncDone: F[Boolean] =
    db.get[String, Boolean](FastSyncDone, ns)

  def putFastSyncDone(b: Boolean = true): F[Unit] =
    db.put[String, Boolean](FastSyncDone, b, ns)

  def getEstimatedHighestBlock: F[BigInt] =
    db.getOptT[String, BigInt](EstimatedHighestBlock, ns).getOrElse(BigInt(0))

  def putEstimatedHighestBlock(n: BigInt): F[Unit] =
    db.put[String, BigInt](EstimatedHighestBlock, n, ns)

  def getSyncStartingBlock: F[BigInt] =
    db.getOptT[String, BigInt](SyncStartingBlock, ns).getOrElse(BigInt(0))

  def putSyncStartingBlock(n: BigInt): F[Unit] =
    db.put[String, BigInt](SyncStartingBlock, n, ns)
}
