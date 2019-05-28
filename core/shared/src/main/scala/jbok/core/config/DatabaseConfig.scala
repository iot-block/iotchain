package jbok.core.config

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._

@ConfiguredJsonCodec
final case class DatabaseConfig(
    driver: String,
    url: String,
    user: String,
    password: String
)
