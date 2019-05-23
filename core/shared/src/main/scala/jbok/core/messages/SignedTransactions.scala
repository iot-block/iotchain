package jbok.core.messages

import io.circe.generic.JsonCodec
import jbok.codec.rlp.implicits._
import jbok.core.models.SignedTransaction

@JsonCodec
final case class SignedTransactions(txs: List[SignedTransaction])
object SignedTransactions {
  val name = "SignedTransactions"

  implicit val rlpCodec: RlpCodec[SignedTransactions] = RlpCodec.gen[SignedTransactions]
}

