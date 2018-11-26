package jbok.core.models

import io.circe._
import io.circe.generic.semiauto._

case class Block(header: BlockHeader, body: BlockBody) {
  lazy val tag: String = s"Block(${header.number})#${header.hash.toHex.take(7)}"
}

object Block {
  implicit val blockJsonEncoder: Encoder[Block] = deriveEncoder[Block]
  implicit val blockJsonDecoder: Decoder[Block] = deriveDecoder[Block]
}
