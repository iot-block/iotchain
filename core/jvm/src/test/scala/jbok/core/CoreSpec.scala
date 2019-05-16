package jbok.core

import java.nio.charset.StandardCharsets

import better.files.Resource
import cats.effect.IO
import distage.Locator
import jbok.common.CommonSpec
import jbok.common.config.Config
import jbok.core.config.Configs.CoreConfig
import jbok.core.config.GenesisConfig
import jbok.core.consensus.poa.clique.Clique
import jbok.core.mining.SimAccount
import jbok.crypto.signature.KeyPair
import monocle.macros.syntax.lens._

import scala.collection.immutable.ListMap

trait CoreSpec extends CommonSpec {
  import CoreTestFixture._

  implicit val chainId = BigInt(1)

  val testMiner       = SimAccount(keyPair, BigInt("1000000000000000000000000"), 0)
  val testAlloc       = ListMap(testMiner.address -> testMiner.balance)
  val testGenesis     = GenesisConfig.generate(chainId, testAlloc)
  val genesis         = Clique.generateGenesisConfig(testGenesis, List(testMiner.address))
  implicit val config = testConfig.copy(genesis = genesis)

  val locator: IO[Locator] =
    CoreModule.withConfig[IO](config).allocated.map(_._1)

  def withConfig(config: CoreConfig): IO[Locator] =
    CoreModule.withConfig[IO](config).allocated.map(_._1)
}

object CoreSpec extends CoreSpec

object CoreTestFixture {
  val keyPair = KeyPair(
    KeyPair.Public("a4991b82cb3f6b2818ce8fedc00ef919ba505bf9e67d96439b63937d24e4d19d509dd07ac95949e815b307769f4e4d6c3ed5d6bd4883af23cb679b251468a8bc"),
    KeyPair.Secret("1a3c21bb6e303a384154a56a882f5b760a2d166161f6ccff15fc70e147161788")
  )

  val testConfig: CoreConfig = Config[IO]
    .read[CoreConfig](Resource.getAsString("config.test.yaml")(StandardCharsets.UTF_8))
    .unsafeRunSync()
    .lens(_.mining.minerKeyPair)
    .set(Some(keyPair))
}
