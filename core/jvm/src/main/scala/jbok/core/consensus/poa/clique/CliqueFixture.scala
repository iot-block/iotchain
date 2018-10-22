package jbok.core.consensus.poa.clique

import cats.effect.IO
import jbok.core.History
import jbok.core.consensus.ConsensusFixture
import jbok.core.mining.TxGen
import jbok.core.pool._
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.common.execution._
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

import scala.concurrent.duration._

trait CliqueFixture extends ConsensusFixture {
  val db           = KeyValueDB.inMemory[IO].unsafeRunSync()
  val history      = History[IO](db).unsafeRunSync()
  val blockPool    = BlockPool[IO](history, BlockPoolConfig()).unsafeRunSync()
  val cliqueConfig = CliqueConfig(period = 1.seconds)

  val txGen         = new TxGen(3)
  val miner         = txGen.addresses.take(1).toList
  val genesisConfig = txGen.genesisConfig.copy(extraData = Clique.fillExtraData(miner.map(_.address)))
  history.loadGenesisConfig(genesisConfig).unsafeRunSync()

  val signer = miner.head

  val sign = (bv: ByteVector) => {
    SecP256k1.sign(bv.toArray, signer.keyPair)
  }
  val clique = Clique[IO](cliqueConfig, history, signer.address, sign)

  println(s"miners: ${miner.map(_.address)}")
  println(s"genesis signers: ${clique.genesisSnapshot.unsafeRunSync().signers}")
  val consensus = new CliqueConsensus[IO](blockPool, clique)
}
