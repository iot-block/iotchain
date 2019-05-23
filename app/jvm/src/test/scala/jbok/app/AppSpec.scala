package jbok.app

import cats.effect.IO
import distage.{Injector, Locator}
import jbok.core.CoreSpec
import jbok.core.config.CoreConfig

trait AppSpec extends CoreSpec {

  val testAppModule =
    testCoreModule(config) ++ new AppModule[IO]

  val testAppResource =
    Injector().produceF[IO](testAppModule).toCats

  override def check(config: CoreConfig)(f: Locator => IO[Unit]): Unit = {

    val objects = locator.unsafeRunSync()

    val p = testAppResource.use { objects =>
      f(objects)
    }
    p.unsafeRunSync()
  }
}
