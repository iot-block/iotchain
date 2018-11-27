package jbok.core.config

import com.typesafe.config.ConfigFactory
import pureconfig._
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._

import scala.reflect.ClassTag

object ConfigLoader {
  val genesis = ConfigFactory.load("genesis").getConfig("genesis")

  val config = ConfigFactory.load().getConfig("jbok")

  def loadGenesis: GenesisConfig = {
    implicit val hint: ProductHint[GenesisConfig] =
      ProductHint[GenesisConfig](fieldMapping = ConfigFieldMapping(CamelCase, CamelCase))

    pureconfig.loadConfigOrThrow[GenesisConfig](genesis)
  }

  def loadOrThrow[A: ClassTag](namespace: String)(implicit reader: Derivation[ConfigReader[A]]): A =
    pureconfig.loadConfigOrThrow[A](config, namespace)
}
