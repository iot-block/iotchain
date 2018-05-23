package jbok.examples.hg

import jbok.core.Transaction
import jbok.crypto.hashing.Hash
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
                      selfParent: Hash,
                      otherParent: Hash,
                      creator: Hash,
                      timestamp: Long,
                      index: Int,
                      txs: List[Transaction]
)

object EventBody {
  implicit val codec: Codec[EventBody] = {
    ("self parent" | Codec[Hash]) ::
      ("other parent" | Codec[Hash]) ::
      ("creator" | Codec[Hash]) ::
      ("timestamp" | int64) ::
      ("index" | int32) ::
      ("transactions" | listOfN(int32, Codec[Transaction]))
  }.as[EventBody]
}
