package jbok.core.config
import io.circe.generic.extras.ConfiguredJsonCodec

import scala.concurrent.duration.FiniteDuration
import jbok.codec.json.implicits._

@ConfiguredJsonCodec
final case class TxPoolConfig(
    poolSize: Int,
    transactionTimeout: FiniteDuration
)

@ConfiguredJsonCodec
final case class BlockPoolConfig(
    maxBlockAhead: Int,
    maxBlockBehind: Int
)
