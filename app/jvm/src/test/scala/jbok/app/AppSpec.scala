package jbok.app

import cats.effect.IO
import distage.Locator
import jbok.core.CoreSpec
import jbok.core.config.CoreConfig
import jbok.crypto.signature.KeyPair

trait AppSpec extends CoreSpec {
  override def check(config: CoreConfig)(f: Locator => IO[Unit]): Unit = {
    val objects = locator.unsafeRunSync()
    val keyPair = objects.get[Option[KeyPair]]
    val p = AppModule.resource[IO](config, keyPair).use { objects =>
      f(objects)
    }
    p.unsafeRunSync()
  }
}
