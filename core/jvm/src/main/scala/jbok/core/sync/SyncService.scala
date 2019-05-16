package jbok.core.sync

import cats.effect.Sync
import cats.implicits._
import jbok.core.config.Configs.SyncConfig
import jbok.core.ledger.History
import jbok.core.messages._
import scodec.bits.ByteVector

final class SyncService[F[_]](config: SyncConfig, history: History[F])(implicit F: Sync[F]) {
  def getBlockHeadersByNumber(start: BigInt, limit: Int): F[BlockHeaders] = {
    val headersCount = limit min config.maxBlockHeadersPerRequest
    val range        = start until start + headersCount
    range.toList.traverse(history.getBlockHeaderByNumber).map(_.flatten).map(BlockHeaders.apply)
  }

  def getBlockHeadersByHash(start: ByteVector, limit: Int): F[BlockHeaders] =
    for {
      blockOpt <- history.getBlockHeaderByHash(start)
      resp <- blockOpt match {
        case Some(header) => getBlockHeadersByNumber(header.number, limit)
        case None         => BlockHeaders(Nil).pure[F]
      }
    } yield resp

  def getBlockBodies(hashes: List[ByteVector]): F[BlockBodies] =
    hashes
      .traverse(hash => history.getBlockBodyByHash(hash))
      .map(xs => BlockBodies(xs.flatten))
}
