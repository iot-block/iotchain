package jbok.app.config

import java.net.InetSocketAddress

import io.circe.generic.JsonCodec
import jbok.core.config.Configs.CoreConfig

@JsonCodec
final case class DatabaseConfig(driver: String, url: String, user: String, password: String)

@JsonCodec
final case class ServiceConfig(
    enabled: Boolean = false,
    secretKey: String,
    host: String,
    port: Int,
    enableGzip: Boolean,
    logBody: Boolean,
    qps: Int
)

@JsonCodec
final case class AppConfig(
    core: CoreConfig,
    db: DatabaseConfig,
    service: ServiceConfig
)

@JsonCodec
final case class RpcConfig(
    enabled: Boolean,
    host: String,
    port: Int,
    apis: String
) {
  val rpcAddr = new InetSocketAddress(host, port)
}
