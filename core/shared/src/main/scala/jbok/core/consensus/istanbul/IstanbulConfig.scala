package jbok.core.consensus.istanbul

import scala.concurrent.duration._

case class IstanbulConfig(
    period: FiniteDuration = 1.seconds, // Number of seconds between blocks to enforce
    epoch: BigInt = BigInt(30000), // Epoch length to reset votes and checkpoint
    defaultDifficulty: BigInt = BigInt(1), // Default block difficulty
    checkpointInterval: BigInt = BigInt(3000),
    proposerPolicy:Int = IstanbulConfig.roundRobin
)
object IstanbulConfig{
  val roundRobin = 0;
  val sticky = 1
}
