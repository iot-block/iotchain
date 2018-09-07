package jbok.core.consensus.poa.clique

import cats.effect.IO
import jbok.core.History
import jbok.core.consensus.ConsensusFixture
import jbok.core.mining.TxGen
import jbok.core.pool._
import jbok.persistent.KeyValueDB

trait CliqueFixture extends ConsensusFixture {
  val db               = KeyValueDB.inMemory[IO].unsafeRunSync()
  val history          = History[IO](db).unsafeRunSync()
  val blockPool        = BlockPool[IO](history, BlockPoolConfig()).unsafeRunSync()
  val cliqueConfig     = CliqueConfig()

  val txGen = new TxGen(3)
  val miners        = txGen.addresses.take(2).toList
  val genesisConfig = txGen.genesisConfig.copy(extraData = Clique.fillExtraData(miners.map(_.address)))
  history.loadGenesisConfig(genesisConfig).unsafeRunSync()

  val clique    = Clique[IO](cliqueConfig, history, miners.head.keyPair)
  val consensus = new CliqueConsensus[IO](clique)
}
