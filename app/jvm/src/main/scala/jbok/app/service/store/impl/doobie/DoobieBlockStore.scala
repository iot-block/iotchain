package jbok.app.service.store.impl.doobie

import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import jbok.app.service.models.Block
import jbok.app.service.store.BlockStore
import jbok.core.models.{Block => CoreBlock}

class DoobieBlockStore(xa: Transactor[IO]) extends BlockStore[IO] {
  def findAllBlocks(page: Int, size: Int): IO[List[Block]] =
    sql"""
         SELECT hash, parentHash, ommersHash, beneficiary, stateRoot, transactionsRoot, receiptsRoot, logsBloom, difficulty, number, gasLimit, gasUsed, unixTimestamp, extra
         FROM blocks
         ORDER BY number DESC
         LIMIT ${size}
         OFFSET ${(page - 1) * size}
       """
      .query[Block]
      .to[List]
      .transact(xa)

  def findBlockByNumber(number: Long): IO[Option[Block]] =
    sql"""
         SELECT hash, parentHash, ommersHash, beneficiary, stateRoot, transactionsRoot, receiptsRoot, logsBloom, difficulty, number, gasLimit, gasUsed, unixTimestamp, extra
         FROM blocks
         WHERE number = ${number}
       """
      .query[Block]
      .option
      .transact(xa)

  def findBlockByHash(hash: String): IO[Option[Block]] =
    sql"""
         SELECT hash, parentHash, ommersHash, beneficiary, stateRoot, transactionsRoot, receiptsRoot, logsBloom, difficulty, number, gasLimit, gasUsed, unixTimestamp, extra
         FROM blocks
         WHERE hash = ${hash}
       """
      .query[Block]
      .option
      .transact(xa)

  def insertBlock(block: CoreBlock): IO[Unit] = {
    val hash             = block.header.hash.toHex
    val parentHash       = block.header.parentHash.toHex
    val ommersHash       = block.header.ommersHash.toHex
    val beneficiary      = block.header.beneficiary.toHex
    val stateRoot        = block.header.stateRoot.toHex
    val transactionsRoot = block.header.transactionsRoot.toHex
    val receiptsRoot     = block.header.receiptsRoot.toHex
    val logsBloom        = block.header.logsBloom.toHex
    val difficulty       = block.header.difficulty.toString()
    val number           = block.header.number.toLong
    val gasLimit         = block.header.gasLimit.toString()
    val gasUsed          = block.header.gasUsed.toString()
    val unixTimestamp    = block.header.unixTimestamp
    val extra            = block.header.extra.toHex

    sql"""
         INSERT OR IGNORE INTO blocks (hash, parentHash, ommersHash, beneficiary, stateRoot, transactionsRoot, receiptsRoot, logsBloom, difficulty, number, gasLimit, gasUsed, unixTimestamp, extra)
         values ($hash, $parentHash, $ommersHash, $beneficiary, $stateRoot, $transactionsRoot, $receiptsRoot, $logsBloom, $difficulty, $number, $gasLimit, $gasUsed, $unixTimestamp, $extra)
       """.update.run
      .transact(xa)
      .void
  }
}
