package jbok.codec.impl

import _root_.io.circe.syntax._
import _root_.io.circe.Json
import jbok.codec._
import cats.implicits._

object circe {
  implicit def circeEncoder[A: _root_.io.circe.Encoder]: Encoder[A, Json] = new Encoder[A, Json] {
    override def encode(arg: A): Json = arg.asJson
  }

  implicit def circeDecoder[A: _root_.io.circe.Decoder]: Decoder[A, Json] = new Decoder[A, Json] {
    override def decode(arg: Json): Either[Throwable, A] =
      arg
        .as[A]
        .leftMap(f => new Exception(s"decoding json ${arg} failed because ${f}"))
  }
}
