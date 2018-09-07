package jbok.core

import cats.Show
import jbok.core.models.{Block, BlockBody, BlockHeader}
import scodec.bits.ByteVector

object PrettyPrinter {
  implicit val showBlockHeader: Show[BlockHeader] = new Show[BlockHeader] {
    override def show(t: BlockHeader): String =
      s"""BlockHeader(
         |  parentHash=${pprint(t.parentHash)}
         |  ommersHash=${pprint(t.ommersHash)}
         |  beneficiary=${pprint(t.beneficiary)}
         |  stateRoot=${pprint(t.stateRoot)}
         |  transactionsRoot=${pprint(t.transactionsRoot)}
         |  receiptsRoot=${pprint(t.receiptsRoot)}
         |  logsBloom=${pprint(t.logsBloom)}
         |  difficulty=${t.difficulty}
         |  number=${t.number},
         |  gasLimit=${t.gasLimit},
         |  gasUsed=${t.gasUsed},
         |  unixTimestamp=${t.unixTimestamp},
         |  extraData=${pprint(t.extraData)},
         |  mixHash=${pprint(t.mixHash)},
         |  nonce=${pprint(t.nonce)}
         |)""".stripMargin
  }

  implicit val showBlockBody: Show[BlockBody] = new Show[BlockBody] {
    override def show(t: BlockBody): String =
      s"""BlockBody(${t.transactionList.length} txs, ${t.uncleNodesList.length} ommers)"""
  }

  implicit val showBlock: Show[Block] = new Show[Block] {
    override def show(t: Block): String =
      s"""Block(
         |header=${pprint(t.header)}
         |body=${pprint(t.body)}
         |)""".stripMargin
  }

  implicit val showByteVector: Show[ByteVector] = new Show[ByteVector] {
    override def show(t: ByteVector): String =
      if (t.isEmpty) "B(empty)"
      else s"B(${t.size} bytes, 0x${t.toHex.take(7)}..)"
  }

  def pprint[A: Show](a: A): String = Show[A].show(a)
}
