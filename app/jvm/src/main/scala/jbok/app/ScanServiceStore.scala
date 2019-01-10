package jbok.app

import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import jbok.core.models.{Address, Block, Receipt}

import scala.concurrent.ExecutionContext

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object ScanServiceStore {
  implicit val cs = IO.contextShift(ExecutionContext.global)

  private[this] val log = jbok.common.log.getLogger("ScanServiceStore")

  val logHandler = {
    import doobie.util.log._
    LogHandler {
      case Success(s, a, e1, e2) =>
        log.trace(s"""Successful Statement Execution:
        | ${s}
        | arguments = [${a.mkString(", ")}]
        |   elapsed = ${e1.toMillis} ms exec + ${e2.toMillis} ms processing (${(e1 + e2).toMillis} ms total)
      """.stripMargin)

      case ProcessingFailure(s, a, e1, e2, t) =>
        log.error(s"""Failed Resultset Processing:
        | ${s}
        | arguments = [${a.mkString(", ")}]
        |   elapsed = ${e1.toMillis} ms exec + ${e2.toMillis} ms processing (failed) (${(e1 + e2).toMillis} ms total)
        |   failure = ${t.getMessage}
      """.stripMargin)

      case ExecFailure(s, a, e1, t) =>
        log.error(s"""Failed Statement Execution:
        | ${s}
        | arguments = [${a.mkString(", ")}]
        |   elapsed = ${e1.toMillis} ms exec (failed)
        |   failure = ${t.getMessage}
      """.stripMargin)
    }
  }

  val xa = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    "jdbc:sqlite:sample.db"
  )

  object DDL {
    val dropTable =
      sql"""DROP TABLE IF EXISTS transactions""".update.run

    val createTable =
      sql"""
        CREATE TABLE transactions (
         id INTEGER PRIMARY KEY AUTOINCREMENT,
         txHash TEXT NOT NULL UNIQUE,
         nonce INTEGER NOT NULL,
         fromAddress TEXT NOT NULL,
         toAddress TEXT NOT NULL,
         value TEXT NOT NULL,
         payload TEXT NOT NULL,
         v TEXT NOT NULL,
         r TEXT NOT NULL,
         s TEXT NOT NULL,
         gasUsed TEXT NOT NULL,
         gasPrice TEXT NOT NULL,
         blockNumber INTEGER NOT NULL,
         blockHash TEXT NOT NULL,
         location INTEGER NOT NULL
       )
        """.update.run

    val createIndex =
      sql"""
        CREATE INDEX IF NOT EXISTS `from_address_index` ON transactions (
          fromAddress
        )
        """.update.run >>
        sql"""
        CREATE INDEX IF NOT EXISTS `to_address_index` ON transactions (
          toAddress
        )
        """.update.run
  }

  def insert(block: Block, receipts: List[Receipt]): IO[Unit] = {
    require(block.body.transactionList.length == receipts.length)
    val xs = block.body.transactionList.zip(receipts).zipWithIndex.map {
      case ((stx, receipt), idx) =>
        (
          stx.hash.toHex,
          stx.nonce.toInt,
          stx.senderAddress.get.toString,
          stx.receivingAddress.toString,
          stx.value.toString,
          stx.payload.toHex,
          stx.v.toString,
          stx.r.toString,
          stx.s.toString,
          receipt.gasUsed.toString,
          stx.gasPrice.toString,
          block.header.number.toLong,
          block.header.hash.toHex,
          idx
        )
    }
    val holes = List.fill(14)("?").mkString(",")
    val sql =
      s"insert into transactions (txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location) values ($holes)"
    Update[(String, Int, String, String, String, String, String, String, String, String, String, Long, String, Int)](
      sql)
      .updateMany(xs)
      .transact(xa)
      .void
  }

  def select(address: Address, start: Long, end: Option[Long], page: Int, size: Int): IO[List[TransactionQueryData]] = {
    val leqEnd = end match {
      case Some(e) => fr"blockNumber <= ${e}"
      case None    => fr""
    }

    (fr"""
       select id, txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       from transactions
       where (fromAddress = ${address.toString} OR toAddress = ${address.toString}) AND
       blockNumber >= ${start} AND """ ++ leqEnd ++
      fr"""
       order by blockNumber, location DESC
       limit ${size}
       offset ${(page - 1) * size}
       """)
      .queryWithLogHandler[TransactionQueryData](logHandler)
      .to[List]
      .transact(xa)
  }
}
