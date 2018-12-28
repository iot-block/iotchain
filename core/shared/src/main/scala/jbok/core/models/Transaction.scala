package jbok.core.models

import io.circe.{Decoder, Encoder}
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Transaction")
@JSExportAll
final case class Transaction(
    nonce: BigInt,
    gasPrice: BigInt,
    gasLimit: BigInt,
    receivingAddress: Option[Address],
    value: BigInt,
    payload: ByteVector
)

object Transaction {
  implicit val txJsonEncoder: Encoder[Transaction] =
    deriveEncoder[Transaction]

  implicit val txJsonDecoder: Decoder[Transaction] =
    deriveDecoder[Transaction]

  def apply(
      nonce: BigInt,
      gasPrice: BigInt,
      gasLimit: BigInt,
      receivingAddress: Address,
      value: BigInt,
      payload: ByteVector
  ): Transaction =
    Transaction(
      nonce,
      gasPrice,
      gasLimit,
      Some(receivingAddress),
      value,
      payload
    )
}
