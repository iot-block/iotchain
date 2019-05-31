package jbok.core.config

import java.net.InetSocketAddress

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._

@ConfiguredJsonCodec
final case class ServiceConfig(
    enable: Boolean,
    enableHttp2: Boolean,
    enableWebsockets: Boolean,
    secure: Boolean,
    logHeaders: Boolean,
    logBody: Boolean,
    host: String,
    port: Int,
    apis: List[String]
) {
  val addr = new InetSocketAddress(host, port)

  val uri: String = (if (secure) "https:" else "http:") + s"//${host}:${port}"
}

@ConfiguredJsonCodec
final case class AppConfig(
    db: DatabaseConfig,
    service: ServiceConfig
)
