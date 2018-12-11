package jbok.app

import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.reference

object ConfigGenerator {
  def withIdentityAndPort(identity: String, port: Int): FullNodeConfig =
    reference.copy(identity = identity, peer = reference.peer.copy(port = port))

  def fill(size: Int): List[FullNodeConfig] =
    (0 until size).toList.map(i => {
      ConfigGenerator.withIdentityAndPort(s"test-${10000 + i}", 10000 + i)
    })
}
