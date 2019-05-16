package jbok.common.log
import io.circe.generic.JsonCodec

@JsonCodec
final case class LogConfig(
    logDir: String,
    prefix: String,
    level: String,
    handler: String
)
