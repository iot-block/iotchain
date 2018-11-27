package jbok.core.models

import io.circe._
import jbok.codec.json.implicits._

case class BlockBody(transactionList: List[SignedTransaction], uncleNodesList: List[BlockHeader])
object BlockBody {
  implicit val bodyJsonEncoder: Encoder[BlockBody] = deriveEncoder[BlockBody]

  implicit val bodyJsonDecoder: Decoder[BlockBody] = deriveDecoder[BlockBody]
}
