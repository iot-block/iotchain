package jbok.core.consensus

import cats.effect.IO
import jbok.core.mining.TxGen

trait ConsensusFixture {
  val consensus: Consensus[IO]
  val txGen: TxGen
}
