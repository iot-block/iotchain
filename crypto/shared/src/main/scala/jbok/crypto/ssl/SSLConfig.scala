package jbok.crypto.ssl
import io.circe.generic.JsonCodec

@JsonCodec
final case class SSLConfig(
    enabled: Boolean,
    keyStorePath: String,
    trustStorePath: String,
    protocol: String,
    clientAuth: String
)
