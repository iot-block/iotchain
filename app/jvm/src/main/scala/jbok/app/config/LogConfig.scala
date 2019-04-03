package jbok.app.config

final case class LogConfig(
    logLevel: String = "info",
    logHandler: String = "console"
)
