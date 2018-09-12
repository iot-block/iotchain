package jbok.core.consensus.poa.clique

import cats.effect.IO
import jbok.core.History
import jbok.core.consensus.ConsensusFixture
import jbok.core.mining.TxGen
import jbok.core.models.Address
import jbok.core.pool._
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

trait CliqueFixture extends ConsensusFixture {
  val db               = KeyValueDB.inMemory[IO].unsafeRunSync()
  val history          = History[IO](db).unsafeRunSync()
  val blockPool        = BlockPool[IO](history, BlockPoolConfig()).unsafeRunSync()
  val cliqueConfig     = CliqueConfig()

  val txGen = new TxGen(3)
  val miners        = txGen.addresses.take(2).toList
  val genesisConfig = txGen.genesisConfig.copy(extraData = Clique.fillExtraData(miners.map(_.address)))
  history.loadGenesisConfig(genesisConfig).unsafeRunSync()

  val signer = (bi: BigInt) => IO {
    miners((bi % miners.length).toInt).address
  }

  val sign = (bi: BigInt, bv: ByteVector) => {
    val keyPair = miners((bi % miners.length).toInt).keyPair
    SecP256k1.sign(bv.toArray, keyPair)
  }
  val clique    = Clique[IO](cliqueConfig, history, signer, sign)

  println(s"miners: ${miners.map(_.address)}")
  println(s"genesis signers: ${clique.genesisSnapshot.unsafeRunSync().signers}")
  val consensus = new CliqueConsensus[IO](clique)
}
