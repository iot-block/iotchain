package jbok.core.models

import io.circe.generic.JsonCodec
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Block")
@JSExportAll
@JsonCodec
final case class Block(header: BlockHeader, body: BlockBody) {
  lazy val tag: String = s"Block(${header.number})#${header.hash.toHex.take(7)}"
}
