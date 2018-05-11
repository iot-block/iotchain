package jbok.examples.hg

import jbok.core.Transaction
import jbok.crypto.hashing.MultiHash
import scodec.codecs._
import scodec.{Codec, _}

/**
  * @param selfParent hash of the previous self created event
  * @param otherParent hash of the received sync event
  * @param creator creator's proposition
  * @param timestamp creator's creating timestamp
  * @param txs optional payload
  *
  */
case class EventBody(
    selfParent: MultiHash,
    otherParent: MultiHash,
    creator: MultiHash,
    timestamp: Long,
    index: Int,
    txs: List[Transaction]
)

object EventBody {
  implicit val codec: Codec[EventBody] = {
    ("self parent" | Codec[MultiHash]) ::
      ("other parent" | Codec[MultiHash]) ::
      ("creator" | Codec[MultiHash]) ::
      ("timestamp" | int64) ::
      ("index" | int32) ::
      ("transactions" | listOfN(int32, Codec[Transaction]))
  }.as[EventBody]
}
