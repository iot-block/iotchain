package jbok.core

import cats.effect.IO
import distage.Locator
import jbok.common.CommonSpec
import jbok.core.config.{CoreConfig, GenesisBuilder}
import jbok.core.mining.SimAccount
import jbok.crypto.signature.KeyPair
import monocle.macros.syntax.lens._

import scala.collection.immutable.ListMap

trait CoreSpec extends CommonSpec {
  implicit val chainId = BigInt(1)
  private val keyPair = KeyPair(
    KeyPair.Public("a4991b82cb3f6b2818ce8fedc00ef919ba505bf9e67d96439b63937d24e4d19d509dd07ac95949e815b307769f4e4d6c3ed5d6bd4883af23cb679b251468a8bc"),
    KeyPair.Secret("1a3c21bb6e303a384154a56a882f5b760a2d166161f6ccff15fc70e147161788")
  )
  val testMiner   = SimAccount(keyPair, BigInt("1000000000000000000000000"), 0)
  val testAlloc   = ListMap(testMiner.address -> testMiner.balance)
  val genesis = GenesisBuilder()
    .withChainId(chainId)
    .addAlloc(testMiner.address, testMiner.balance)
    .addMiner(testMiner.address)
    .build

  implicit val config = CoreModule.testConfig
    .lens(_.mining.secret).set(keyPair.secret.bytes)
    .lens(_.genesis).set(genesis)

  val locator: IO[Locator] =
    CoreModule.resource[IO](config).allocated.map(_._1)

  def check(f: Locator => IO[Unit]): Unit =
    check(config)(f)

  def check(config: CoreConfig)(f: Locator => IO[Unit]): Unit = {
    val p = CoreModule.resource[IO](config).use { objects =>
      f(objects)
    }
    p.unsafeRunSync()
  }
}

object CoreSpec extends CoreSpec
