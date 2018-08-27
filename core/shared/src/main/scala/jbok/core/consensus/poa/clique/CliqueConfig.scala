package jbok.core.consensus.poa.clique

import scala.concurrent.duration._

// CliqueConfig is the consensus engine configs for proof-of-authority based sealing.
case class CliqueConfig(
    period: FiniteDuration = 15.seconds, // Number of seconds between blocks to enforce
    epoch: BigInt = BigInt(30000) // Epoch length to reset votes and checkpoint
)
