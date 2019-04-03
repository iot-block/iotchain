package jbok.app.config

import jbok.core.config.Configs.CoreConfig

final case class PeerNodeConfig(
    identity: String,
    core: CoreConfig,
    log: LogConfig,
    service: ServiceConfig
)
