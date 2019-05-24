package jbok.app.service.store

import scodec.bits.ByteVector

trait BlockStore[F[_]] {
  def getBestBlockNumber: F[Option[BigInt]]

  def getBestBlockNumberAndHash: F[(BigInt, ByteVector)]

  def getBlockHashByNumber(number: BigInt): F[Option[ByteVector]]

  def delByBlockNumber(number: BigInt): F[Unit]

  def insert(number: BigInt, hash: ByteVector): F[Unit]
}
