package jbok.app

import cats.effect.IO
import distage.{Injector, Locator}
import jbok.core.CoreSpec
import jbok.core.config.FullConfig

trait AppSpec extends CoreSpec {

  def testAppModule(config: FullConfig) =
    testCoreModule(config) ++ new AppModule[IO]

  def testAppResource(config: FullConfig) =
    Injector().produceF[IO](testAppModule(config)).toCats

  override def check(f: Locator => IO[Unit]): Unit =
    check(config)(f)

  override def check(config: FullConfig)(f: Locator => IO[Unit]): Unit = {
    val p = testAppResource(config).use { objects =>
      f(objects)
    }
    p.unsafeRunSync()
  }
}
