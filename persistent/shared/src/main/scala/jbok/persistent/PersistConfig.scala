package jbok.persistent

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._

@ConfiguredJsonCodec
final case class PersistConfig(driver: String, path: String, columnFamilies: List[String])
