package jbok.app.config

import scodec.bits.ByteVector

final case class ServiceConfig(
    enabled: Boolean,
    host: String,
    port: Int,
    dbUrl: String,
    secretKey: String,
    qps: Int
) {
  require(ByteVector.fromHex(secretKey).isDefined, "secretKey MUST be a valid Hex string")
  require(secretKey.length == 64, "secretKey length MUST be 64")
}
