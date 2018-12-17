package jbok.core.consensus.istanbul

import _root_.io.circe.generic.JsonCodec
import jbok.codec.json.implicits._
import scala.concurrent.duration._

@JsonCodec
case class IstanbulConfig(
    period: FiniteDuration = 1.seconds, // Number of seconds between blocks to enforce
    epoch: Int = 30000, // Epoch length to reset votes and checkpoint
    defaultDifficulty: BigInt = BigInt(1), // Default block difficulty
    checkpointInterval: BigInt = BigInt(3000),
    proposerPolicy:Int = IstanbulConfig.roundRobin,
    requestTimeout:Int = 10*1000
)
object IstanbulConfig{
  val roundRobin = 0;
  val sticky = 1
}
