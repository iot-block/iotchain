package jbok.core.config

import jbok.codec.json.implicits._
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
final case class KeyStoreConfig(
    dir: String
)
