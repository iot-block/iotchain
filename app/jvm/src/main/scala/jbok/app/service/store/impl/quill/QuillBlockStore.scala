package jbok.app.service.store.impl.quill

import cats.effect.{ConcurrentEffect, IO}
import cats.implicits._
import jbok.app.service.models.Block
import jbok.app.service.store.BlockStore
import jbok.core.models.{Block => CoreBlock}
import monix.eval.Task

class QuillBlockStore(ctx: Quill.Ctx)(implicit F: ConcurrentEffect[Task]) extends BlockStore[IO] {
  import ctx._

  private val Blocks = quote(querySchema[Block]("blocks"))

  override def findAllBlocks(page: Int, size: Int): IO[List[Block]] = {
    val q = quote {
      Blocks.drop(lift((page - 1) * size)).take(lift(size))
    }

    ctx.run(q).toIO
  }

  override def findBlockByNumber(number: Long): IO[Option[Block]] = {
    val q = quote {
      Blocks.filter(_.number == lift(number))
    }
    ctx.run(q).toIO.map(_.headOption)
  }

  override def findBlockByHash(hash: String): IO[Option[Block]] = {
    val q = quote {
      Blocks.filter(_.hash == lift(hash))
    }

    ctx.run(q).toIO.map(_.headOption)
  }

  override def insertBlock(block: CoreBlock): IO[Unit] = {
    val b = Block(
      block.header.hash.toHex,
      block.header.parentHash.toHex,
      block.header.ommersHash.toHex,
      block.header.beneficiary.toHex,
      block.header.stateRoot.toHex,
      block.header.transactionsRoot.toHex,
      block.header.receiptsRoot.toHex,
      block.header.logsBloom.toHex,
      block.header.difficulty.toString(),
      block.header.number.toLong,
      block.header.gasLimit.toString(),
      block.header.gasUsed.toString(),
      block.header.unixTimestamp,
      block.header.extra.toHex
    )
    val q = quote(Blocks.insert(lift(b)))
    ctx.run(q).toIO.void
  }
}
