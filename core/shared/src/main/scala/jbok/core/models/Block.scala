package jbok.core.models

import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Block")
@JSExportAll
final case class Block(header: BlockHeader, body: BlockBody) {
  lazy val tag: String = s"Block(${header.number})#${header.hash.toHex.take(7)}"
}

object Block {
  implicit val blockJsonEncoder = deriveEncoder[Block]
  implicit val blockJsonDecoder = deriveDecoder[Block]
}
