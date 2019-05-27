package jbok.codec.json

import cats.effect.IO
import cats.implicits._
import jbok.common.CommonSpec
import io.circe.parser._
import io.circe.syntax._

class JsonCodecSpec extends CommonSpec {
  "JsonCodec" should {
    "codec large BigInt" in {
      val bigInt  = "\"999999999999999989998999958000\""
      val bigInt2 = "999999999999999989998999958e3"
      for {
        x <- IO.fromEither(decode[BigInt](bigInt))
        _ = x shouldBe BigInt("999999999999999989998999958000")

        x <- IO.fromEither(decode[BigInt](bigInt2))
        _ = x shouldBe BigInt("1000000000000000000000000000000")

        json = BigInt("999999999999999989998999958000").asJson.noSpaces
        _    = json shouldBe "999999999999999989998999958e3"
      } yield ()
    }

    "codec large BigInt custom" in {
      import jbok.codec.json.implicits._
      for {
        json <- BigInt("999999999999999989998999958000").asJson.noSpaces.pure[IO]
        _ = json shouldBe "\"999999999999999989998999958000\""
      } yield ()
    }
  }
}
