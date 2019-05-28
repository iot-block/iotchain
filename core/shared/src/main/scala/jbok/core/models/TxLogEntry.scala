package jbok.core.models

import io.circe.generic.extras.ConfiguredJsonCodec
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("TxLogEntry")
@JSExportAll
@ConfiguredJsonCodec
final case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)
