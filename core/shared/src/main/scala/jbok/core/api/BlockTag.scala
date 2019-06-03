package jbok.core.api

import io.circe.{Decoder, Encoder}
import jbok.common.math.N

sealed trait BlockTag
object BlockTag {
  case object latest                extends BlockTag
  final case class Number(value: N) extends BlockTag

  def apply(number: N): Number = Number(number)
  def apply(s: String): Number = Number(N(s))
  def apply(i: Int): Number    = Number(N(i))
  def apply(l: Long): Number   = Number(N(l))

  implicit val encoder: Encoder[BlockTag] = Encoder.encodeString.contramap[BlockTag] {
    case BlockTag.latest        => "latest"
    case BlockTag.Number(value) => value.toString()
  }
  implicit val decoder: Decoder[BlockTag] = Decoder.decodeString.map[BlockTag] {
    case "latest" => BlockTag.latest
    case number   => BlockTag(number)
  }
}
