package jbok.core.config

import com.typesafe.config.ConfigFactory
import jbok.core.models
import jbok.core.models.UInt256
import pureconfig._
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._

import scala.reflect.ClassTag

object ConfigLoader {
  val genesis = ConfigFactory.load("genesis").getConfig("genesis")

  val config = ConfigFactory.load().getConfig("jbok")

  implicit val bigIntReader: ConfigReader[BigInt] = ConfigReader.fromString[BigInt](
    ConvertHelpers.catchReadError(s => BigInt(s))
  )

  implicit val uint256Reader: ConfigReader[UInt256] = ConfigReader.fromString[UInt256](
    ConvertHelpers.catchReadError(s => models.UInt256(s.toInt))
  )

  implicit val hint: ProductHint[GenesisConfig] =
    ProductHint[GenesisConfig](fieldMapping = ConfigFieldMapping(CamelCase, CamelCase), allowUnknownKeys = false)

  def loadGenesis: GenesisConfig = {
    pureconfig.loadConfigOrThrow[GenesisConfig](genesis)
  }

  def loadOrThrow[A: ClassTag](namespace: String)(implicit reader: Derivation[ConfigReader[A]]): A =
    pureconfig.loadConfigOrThrow[A](config, namespace)
}
