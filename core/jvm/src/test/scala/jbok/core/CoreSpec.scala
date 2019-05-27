package jbok.core

import cats.effect.{IO, Resource}
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import distage.{Injector, Locator}
import jbok.common.CommonSpec
import jbok.core.config.{FullConfig, GenesisBuilder}
import jbok.core.keystore.{KeyStore, MockingKeyStore}
import jbok.core.models.Address
import jbok.crypto.signature.KeyPair
import monocle.macros.syntax.lens._

object CoreSpecFixture {
  val chainId = BigInt(1)

  val testKeyPair = KeyPair(
    KeyPair.Public("a4991b82cb3f6b2818ce8fedc00ef919ba505bf9e67d96439b63937d24e4d19d509dd07ac95949e815b307769f4e4d6c3ed5d6bd4883af23cb679b251468a8bc"),
    KeyPair.Secret("1a3c21bb6e303a384154a56a882f5b760a2d166161f6ccff15fc70e147161788")
  )
  val testAllocAddress = Address(testKeyPair)
  val testAllocBalance = BigInt("1" + "0" * 30)

  val testGenesis = GenesisBuilder()
    .withChainId(chainId)
    .addAlloc(testAllocAddress, testAllocBalance)
    .addMiner(testAllocAddress)
    .build

  val config = CoreModule.testConfig
    .lens(_.genesis)
    .set(testGenesis)

  val keystoreModule: ModuleDef = new ModuleDef {
    make[KeyStore[IO]].fromEffect(MockingKeyStore[IO](testKeyPair :: Nil))
  }
}

trait CoreSpec extends CommonSpec {
  implicit val chainId = CoreSpecFixture.chainId

  implicit val config = CoreSpecFixture.config

  val genesis = CoreSpecFixture.testGenesis

  val testKeyPair = CoreSpecFixture.testKeyPair

  val testAllocAddress = CoreSpecFixture.testAllocAddress

  def testCoreModule(config: FullConfig) =
    new CoreModule[IO](config).overridenBy(CoreSpecFixture.keystoreModule)

  def testCoreResource(config: FullConfig): Resource[IO, Locator] =
    Injector().produceF[IO](testCoreModule(config)).toCats

  val locator: IO[Locator] = testCoreResource(config).allocated.map(_._1)

  def check(f: Locator => IO[Unit]): Unit =
    check(config)(f)

  def check(config: FullConfig)(f: Locator => IO[Unit]): Unit = {
    val p = testCoreResource(config).use { objects =>
      f(objects)
    }
    p.unsafeRunSync()
  }
}

object CoreSpec extends CoreSpec
