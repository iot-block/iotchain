package jbok.core.messages

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.core.models.SignedTransaction

@ConfiguredJsonCodec
final case class SignedTransactions(txs: List[SignedTransaction])
object SignedTransactions {
  val name = "SignedTransactions"

  implicit val rlpCodec: RlpCodec[SignedTransactions] = RlpCodec.gen[SignedTransactions]
}
