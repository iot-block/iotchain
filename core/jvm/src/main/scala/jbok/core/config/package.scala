package jbok.core
import cats.effect.IO
import jbok.core.config.Configs.FullNodeConfig

package object config {
  val referenceConfig: FullNodeConfig = ConfigLoader.loadFullNodeConfig[IO](ConfigHelper.reference).unsafeRunSync()
}
