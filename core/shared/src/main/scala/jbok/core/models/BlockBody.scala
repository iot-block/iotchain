package jbok.core.models

import scodec.Codec
import scodec.codecs._

case class BlockBody(transactionList: List[SignedTransaction], uncleNodesList: List[BlockHeader])
object BlockBody {
  implicit val codec: Codec[BlockBody] =
    (list(Codec[SignedTransaction]) :: list(Codec[BlockHeader])).as[BlockBody]
}
