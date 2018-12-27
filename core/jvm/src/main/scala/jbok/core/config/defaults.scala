package jbok.core.config

import cats.effect.IO
import jbok.core.config.Configs.FullNodeConfig
import com.typesafe.config._

object defaults {
  val reference: FullNodeConfig = ConfigLoader.loadFullNodeConfig[IO](ConfigHelper.reference).unsafeRunSync()

  val testOverrides = ConfigHelper
    .parseConfig(
      List(
        "-history.chainDataDir",
        "inmem",
        "-peer.peerDataDir",
        "inmem",
        "-logLevel",
        "DEBUG",
      ))
    .right
    .get

  val testReference = ConfigLoader.loadFullNodeConfig[IO](ConfigHelper.overrideWith(testOverrides)).unsafeRunSync()

  def genTestReference(config: Config): FullNodeConfig =
    ConfigLoader.loadFullNodeConfig[IO](ConfigHelper.overrideWith(config)).unsafeRunSync()
}
