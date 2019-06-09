package jbok.common.log
import io.circe.generic.JsonCodec

import scala.concurrent.duration.FiniteDuration

@JsonCodec
final case class LogConfig(
    logDir: String,
    level: String,
    maxLogs: Int
)
