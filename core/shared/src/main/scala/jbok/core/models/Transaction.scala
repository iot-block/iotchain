package jbok.core.models

import io.circe.generic.extras.ConfiguredJsonCodec
import scodec.bits.ByteVector
import jbok.codec.json.implicits._
import jbok.common.math.N

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Transaction")
@JSExportAll
@ConfiguredJsonCodec
final case class Transaction(
    nonce: N,
    gasPrice: N,
    gasLimit: N,
    receivingAddress: Option[Address],
    value: N,
    payload: ByteVector
)
