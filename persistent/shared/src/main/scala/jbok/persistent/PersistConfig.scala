package jbok.persistent
import io.circe.generic.JsonCodec

@JsonCodec
final case class PersistConfig(driver: String, path: String, columnFamilies: List[String])
