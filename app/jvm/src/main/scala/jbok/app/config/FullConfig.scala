package jbok.app.config

import io.circe.generic.JsonCodec
import jbok.core.config.CoreConfig

@JsonCodec
final case class FullConfig(
    core: CoreConfig,
    app: AppConfig
)
