package jbok.core.models

import io.circe.generic.JsonCodec
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("BlockBody")
@JSExportAll
@JsonCodec
final case class BlockBody(transactionList: List[SignedTransaction], ommerList: List[BlockHeader])
