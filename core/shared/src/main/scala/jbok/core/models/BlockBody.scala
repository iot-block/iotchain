package jbok.core.models

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("BlockBody")
@JSExportAll
@ConfiguredJsonCodec
final case class BlockBody(transactionList: List[SignedTransaction])
