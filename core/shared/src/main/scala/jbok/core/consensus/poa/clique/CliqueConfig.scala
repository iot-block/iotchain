package jbok.core.consensus.poa.clique

import io.circe.generic.JsonCodec
import jbok.codec.json.implicits._

import scala.concurrent.duration._

// CliqueConfig is the consensus engine configs for proof-of-authority based sealing.
@JsonCodec
case class CliqueConfig(
    period: FiniteDuration = 15.seconds, // Number of seconds between blocks to enforce
    epoch: BigInt = BigInt(30000) // Epoch length to reset votes and checkpoint
)

