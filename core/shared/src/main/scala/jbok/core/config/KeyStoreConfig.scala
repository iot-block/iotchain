package jbok.core.config
import io.circe.generic.JsonCodec

@JsonCodec
final case class KeyStoreConfig(
    dir: String
)
