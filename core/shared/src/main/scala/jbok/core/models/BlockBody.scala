package jbok.core.models

import io.circe._
import jbok.codec.json.implicits._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("BlockBody")
@JSExportAll
final case class BlockBody(transactionList: List[SignedTransaction], ommerList: List[BlockHeader])
object BlockBody {
  implicit val bodyJsonEncoder: Encoder[BlockBody] = deriveEncoder[BlockBody]

  implicit val bodyJsonDecoder: Decoder[BlockBody] = deriveDecoder[BlockBody]
}
