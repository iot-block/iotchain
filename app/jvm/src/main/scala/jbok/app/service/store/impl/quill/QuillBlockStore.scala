package jbok.app.service.store.impl.quill

import io.getquill._
import cats.effect.IO
import jbok.app.service.models.Block
import jbok.app.service.store.BlockStore
import jbok.core.models.{Block => CoreBlock}

class QuillBlockStore(ctx: SqliteJdbcContext[Literal.type]) extends BlockStore[IO] {
  import ctx.{IO => _, _}

  private val Blocks = quote(querySchema[Block]("blocks"))

  override def findAllBlocks(page: Int, size: Int): IO[List[Block]] = IO {
    val q = quote {
      Blocks.drop(lift((page - 1) * size)).take(lift(size))
    }

    ctx.run(q)
  }

  override def findBlockByNumber(number: Long): IO[Option[Block]] = IO {
    val q = quote {
      Blocks.filter(_.number == lift(number))
    }
    ctx.run(q).headOption
  }

  override def findBlockByHash(hash: String): IO[Option[Block]] = IO {
    val q = quote {
      Blocks.filter(_.hash == lift(hash))
    }

    ctx.run(q).headOption
  }

  override def insertBlock(block: CoreBlock): IO[Unit] = IO {
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
    ctx.run(q)
  }
}
