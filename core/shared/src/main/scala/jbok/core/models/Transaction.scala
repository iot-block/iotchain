package jbok.core.models

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder}
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Transaction")
@JSExportAll
@JsonCodec
final case class Transaction(
    nonce: BigInt,
    gasPrice: BigInt,
    gasLimit: BigInt,
    receivingAddress: Option[Address],
    value: BigInt,
    payload: ByteVector
)
