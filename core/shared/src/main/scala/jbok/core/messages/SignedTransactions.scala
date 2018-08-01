package jbok.core.messages

import jbok.core.models.SignedTransaction

case class SignedTransactions(txs: List[SignedTransaction]) extends Message {
  override def toString: String =
    s"""
       |SignedTransactions(
       |${txs.mkString("\n")}
       |)
     """.stripMargin
}
