package jbok.app.config

import scodec.bits.ByteVector

final case class ServiceConfig(
    enabled: Boolean = false,
    host: String = "localhost",
    port: Int = 10087,
    dbUrl: String = "",
    secretKey: String = "",
    qps: Int = 0
) {
  require(ByteVector.fromHex(secretKey).isDefined, "secretKey MUST be a valid Hex string")
  require(secretKey.length == 64, "secretKey length MUST be 64")
}
