package jbok.app.config
import io.circe.generic.JsonCodec

@JsonCodec
final case class DatabaseConfig(
    driver: String,
    url: String,
    user: String,
    password: String
)
