package jbok.app.service.store

import jbok.app.service.models.{Block, Transaction}
import jbok.core.models.{Receipt, Block => CoreBlock}

trait BlockStore[F[_]] {
  def findAllBlocks(page: Int, size: Int): F[List[Block]]

  def findBlockByNumber(number: Long): F[Option[Block]]

  def findBlockByHash(hash: String): F[Option[Block]]

  def insertBlock(block: CoreBlock): F[Unit]
}
