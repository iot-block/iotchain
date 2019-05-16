package jbok.app

import cats.effect.IO
import distage.Locator
import jbok.app.config.FullConfig
import jbok.core.CoreSpec

trait AppSpec extends CoreSpec {
  override val locator: IO[Locator] =
    AppModule.resource[IO](FullConfig(config, AppModule.appConfig)).allocated.map(_._1)
}
