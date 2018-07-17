package jbok.network.server

import cats.Applicative
import cats.effect.Sync
import io.circe._
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}

trait Http4sCodec[F[_]] {
  implicit def entityEncoder[A](
      implicit enc: Encoder[A],
      ev: EntityEncoder[F, String],
      F: Applicative[F]): EntityEncoder[F, A] =
    jsonEncoderOf[F, A](ev, F, enc)

  implicit def entityDecoder[A](implicit d: Decoder[A], F: Sync[F]): EntityDecoder[F, A] = jsonOf[F, A](F, d)
}
