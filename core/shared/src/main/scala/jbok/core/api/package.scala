package jbok.core

import io.circe.{Decoder, Encoder}

package object api {
  type BlockTag = Either[BigInt, String]
  object BlockTag {
    val latest: BlockTag                = Right("latest")
    def apply(number: BigInt): BlockTag = Left(number)

    implicit val encoder: Encoder[BlockTag] = Encoder[String].contramap(_.fold(_.toString, identity))
    implicit val decoder: Decoder[BlockTag] = Decoder[String].map {
      case "latest" => latest
      case number   => apply(BigInt(number))
    }
  }

}
