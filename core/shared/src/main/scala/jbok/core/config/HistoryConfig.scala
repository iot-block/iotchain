package jbok.core.config

import io.circe.generic.JsonCodec
import jbok.core.models.UInt256
import jbok.codec.json.implicits._

@JsonCodec
final case class HistoryConfig(
    frontierBlockNumber: BigInt,
    homesteadBlockNumber: BigInt,
    tangerineWhistleBlockNumber: BigInt,
    spuriousDragonBlockNumber: BigInt,
    byzantiumBlockNumber: BigInt,
    constantinopleBlockNumber: BigInt,
    difficultyBombPauseBlockNumber: BigInt,
    difficultyBombContinueBlockNumber: BigInt
) {
  val accountStartNonce: UInt256  = UInt256.Zero
  val maxCodeSize: Option[BigInt] = Some(24 * 1024)
}
