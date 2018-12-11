package jbok.core
import cats.effect.IO
import jbok.core.config.Configs.FullNodeConfig

package object config {
  val reference: FullNodeConfig = ConfigLoader.loadFullNodeConfig[IO](ConfigHelper.reference).unsafeRunSync()
}
