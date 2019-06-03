package jbok.app.service.store

import jbok.common.math.N
import scodec.bits.ByteVector

trait BlockStore[F[_]] {
  def getBestBlockNumber: F[Option[N]]

  def getBestBlockNumberAndHash: F[(N, ByteVector)]

  def getBlockHashByNumber(number: N): F[Option[ByteVector]]

  def delByBlockNumber(number: N): F[Unit]

  def insert(number: N, hash: ByteVector): F[Unit]
}
