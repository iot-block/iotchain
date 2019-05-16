package jbok.app.service.store.doobie

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import jbok.app.service.models.Block
import jbok.app.service.store.BlockStore
import jbok.core.models.{Block => CoreBlock}

final class DoobieBlockStore[F[_]](xa: Transactor[F])(implicit F: Sync[F]) extends BlockStore[F] {

  def findAllBlocks(page: Int, size: Int): F[List[Block]] =
    (sql"""
       select blockHash, parentHash, ommersHash, beneficiary, stateRoot, transactionsRoot, receiptsRoot, logsBloom, difficulty, blockNumber, gasLimit, gasUsed, unixTimestamp, extra
       from blocks
       order by blockNumber desc
       """ ++ Doobie.limit(page, size))
      .query[Block]
      .to[List]
      .transact(xa)

  def findBlockByNumber(number: Long): F[Option[Block]] =
    sql"""
         select blockHash, parentHash, ommersHash, beneficiary, stateRoot, transactionsRoot, receiptsRoot, logsBloom, difficulty, blockNumber, gasLimit, gasUsed, unixTimestamp, extra
         from blocks
         where blockNumber = ${number}
       """
      .query[Block]
      .option
      .transact(xa)

  def findBlockByHash(hash: String): F[Option[Block]] =
    sql"""
         select blockHash, parentHash, ommersHash, beneficiary, stateRoot, transactionsRoot, receiptsRoot, logsBloom, difficulty, blockNumber, gasLimit, gasUsed, unixTimestamp, extra
         from blocks
         where blockHash = ${hash}
       """
      .query[Block]
      .option
      .transact(xa)

  def insertBlock(block: CoreBlock): F[Unit] = {
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
         insert ignore into blocks (blockHash, parentHash, ommersHash, beneficiary, stateRoot, transactionsRoot, receiptsRoot, logsBloom, difficulty, blockNumber, gasLimit, gasUsed, unixTimestamp, extra)
         values ($hash, $parentHash, $ommersHash, $beneficiary, $stateRoot, $transactionsRoot, $receiptsRoot, $logsBloom, $difficulty, $number, $gasLimit, $gasUsed, $unixTimestamp, $extra)
       """.update.run
      .transact(xa)
      .void
  }
}
