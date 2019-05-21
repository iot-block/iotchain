package jbok.core.config
import io.circe.generic.JsonCodec

import scala.concurrent.duration.FiniteDuration
import jbok.codec.json.implicits._

@JsonCodec
final case class TxPoolConfig(
    poolSize: Int,
    transactionTimeout: FiniteDuration
)

@JsonCodec
final case class BlockPoolConfig(
    maxBlockAhead: Int,
    maxBlockBehind: Int
)
