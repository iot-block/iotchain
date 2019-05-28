package jbok.crypto.ssl

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._

@ConfiguredJsonCodec
final case class SSLConfig(
    enabled: Boolean,
    keyStorePath: String,
    trustStorePath: String,
    protocol: String,
    clientAuth: String
)
