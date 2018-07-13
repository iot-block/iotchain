package jbok.core.models

import scodec.Codec
import jbok.codec.codecs._

case class BlockBody(transactionList: List[SignedTransaction], uncleNodesList: List[BlockHeader])
object BlockBody {
  implicit val codec: Codec[BlockBody] =
    (codecList[SignedTransaction] :: codecList[BlockHeader]).as[BlockBody]
}
