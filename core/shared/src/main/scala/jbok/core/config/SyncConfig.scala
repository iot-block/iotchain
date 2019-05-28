package jbok.core.config

import io.circe.generic.extras.ConfiguredJsonCodec

import scala.concurrent.duration.FiniteDuration
import jbok.codec.json.implicits._

@ConfiguredJsonCodec
final case class SyncConfig(
    maxBlockHeadersPerRequest: Int,
    maxBlockBodiesPerRequest: Int,
    offset: Int,
    checkInterval: FiniteDuration,
    banDuration: FiniteDuration,
    requestTimeout: FiniteDuration
)
