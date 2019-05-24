package jbok.app.service.store.doobie

import cats.effect.Sync
import cats.implicits._
import Doobie._
import doobie.util.transactor.Transactor
import jbok.app.service.store.BlockStore
import scodec.bits.ByteVector
import doobie.implicits._

final class DoobieBlockStore[F[_]](xa: Transactor[F])(implicit F: Sync[F]) extends BlockStore[F] with DoobieSupport {
  override def getBestBlockNumber: F[Option[BigInt]] =
    sql"""
      SELECT blockNumber
      FROM blocks
      ORDER BY blockNumber DESC
      LIMIT 1
      """
      .query[BigInt]
      .option
      .transact(xa)

  override def getBestBlockNumberAndHash: F[(BigInt, ByteVector)] =
    sql"""
      SELECT blockNumber, blockHash
      FROM blocks
      ORDER BY blockNumber DESC
      LIMIT 1
      """
      .query[(BigInt, ByteVector)]
      .unique
      .transact(xa)

  override def getBlockHashByNumber(number: BigInt): F[Option[ByteVector]] =
    sql"""
      SELECT blockHash
      FROM blocks
      WHERE blockNumber = $number
      """
      .query[ByteVector]
      .option
      .transact(xa)

  override def delByBlockNumber(number: BigInt): F[Unit] =
    sql"""
      DELETE FROM blocks where blockNumber = $number
      """.update.run
      .transact(xa)
      .void

  override def insert(number: BigInt, hash: ByteVector): F[Unit] =
    sql"""
      INSERT INTO blocks (blockNumber, blockHash) VALUES ($number, $hash)
      """.update.run.void.transact(xa)
}
