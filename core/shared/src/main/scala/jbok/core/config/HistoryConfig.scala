package jbok.core.config

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.core.models.UInt256
import jbok.codec.json.implicits._
import jbok.common.math.N

@ConfiguredJsonCodec
final case class HistoryConfig(
    frontierBlockNumber: N,
    homesteadBlockNumber: N,
    tangerineWhistleBlockNumber: N,
    spuriousDragonBlockNumber: N,
    byzantiumBlockNumber: N,
    constantinopleBlockNumber: N
) {
  val accountStartNonce: UInt256  = UInt256.zero
  val maxCodeSize: Option[N] = Some(24 * 1024L)
}
