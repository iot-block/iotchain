package jbok.app.service.store.doobie

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import jbok.app.service.store.BlockStore
import jbok.common.math.N
import scodec.bits.ByteVector

final class DoobieBlockStore[F[_]](xa: Transactor[F])(implicit F: Sync[F]) extends BlockStore[F] with DoobieSupport {
  override def getBestBlockNumber: F[Option[N]] =
    sql"""
      SELECT blockNumber
      FROM blocks
      ORDER BY blockNumber DESC
      LIMIT 1
      """
      .query[N]
      .option
      .transact(xa)

  override def getBestBlockNumberAndHash: F[(N, ByteVector)] =
    sql"""
      SELECT blockNumber, blockHash
      FROM blocks
      ORDER BY blockNumber DESC
      LIMIT 1
      """
      .query[(N, ByteVector)]
      .unique
      .transact(xa)

  override def getBlockHashByNumber(number: N): F[Option[ByteVector]] =
    sql"""
      SELECT blockHash
      FROM blocks
      WHERE blockNumber = $number
      """
      .query[ByteVector]
      .option
      .transact(xa)

  override def delByBlockNumber(number: N): F[Unit] =
    sql"""
      DELETE FROM blocks where blockNumber = $number
      """.update.run
      .transact(xa)
      .void

  override def insert(number: N, hash: ByteVector): F[Unit] =
    sql"""
      INSERT INTO blocks (blockNumber, blockHash) VALUES ($number, $hash)
      """.update.run.void.transact(xa)
}
