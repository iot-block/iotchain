package jbok.core.messages

import jbok.core.models.SignedTransaction
import jbok.codec.codecs._
import scodec.Codec

case class SignedTransactions(txs: List[SignedTransaction]) extends Message {
  override def toString: String =
    s"""
       |SignedTransactions(
       |${txs.mkString("\n")}
       |)
     """.stripMargin
}
object SignedTransactions {
  implicit val codec: Codec[SignedTransactions] = codecList[SignedTransaction].as[SignedTransactions]
}
