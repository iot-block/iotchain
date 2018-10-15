package jbok.core.consensus

import cats.effect.IO
import jbok.core.config.GenesisConfig
import jbok.core.mining.TxGen

trait ConsensusFixture {
  val consensus: Consensus[IO]
  val txGen: TxGen
  val genesisConfig: GenesisConfig
}
