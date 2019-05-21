package jbok.core.config

import io.circe.generic.JsonCodec
import jbok.core.models.Address
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration
import jbok.codec.json.implicits._

@JsonCodec
final case class MiningConfig(
    enabled: Boolean,
    secret: ByteVector,
    coinbase: Address,
    period: FiniteDuration,
    epoch: Int,
    checkpointInterval: Int,
    minBroadcastPeers: Int
)
