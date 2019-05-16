package jbok.core.config

import io.circe.generic.JsonCodec
import jbok.core.models.UInt256
import jbok.codec.json.implicits._

@JsonCodec
final case class HistoryConfig(
    frontierBlockNumber: BigInt = 0,
    homesteadBlockNumber: BigInt = 1150000,
    tangerineWhistleBlockNumber: BigInt = 2463000,
    spuriousDragonBlockNumber: BigInt = 2675000,
    byzantiumBlockNumber: BigInt = 4370000,
    constantinopleBlockNumber: BigInt = BigInt("1000000000000000000000"), // TBD on the Ethereum mainnet
    difficultyBombPauseBlockNumber: BigInt = BigInt("3000000"),
    difficultyBombContinueBlockNumber: BigInt = BigInt("5000000")
) {
  val accountStartNonce: UInt256  = UInt256.Zero
  val maxCodeSize: Option[BigInt] = Some(24 * 1024)
}
