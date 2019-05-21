package jbok.app

import cats.effect.IO
import distage.Locator
import jbok.core.CoreSpec
import jbok.core.config.CoreConfig

trait AppSpec extends CoreSpec {
  override def check(config: CoreConfig)(f: Locator => IO[Unit]): Unit = {
    val p = AppModule.resource[IO](config).use { objects =>
      f(objects)
    }
    p.unsafeRunSync()
  }
}
