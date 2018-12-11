package jbok.core.config

import cats.effect.Sync
import cats.implicits._
import com.typesafe.config.Config
import jbok.core.config.Configs._
import jbok.core.models.UInt256
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.generic.ProductHint
import scodec.bits.ByteVector

import scala.reflect.ClassTag

object ConfigLoader {
  def loadFullNodeConfig[F[_]](config: Config)(implicit F: Sync[F]): F[FullNodeConfig] =
    for {
      datadir  <- F.delay(config.getString("datadir"))
      identity <- F.delay(config.getString("identity"))
      genesis  <- F.delay(ConfigLoader.loadOrThrow[GenesisConfig](config, "genesis"))
      history  <- F.delay(ConfigLoader.loadOrThrow[HistoryConfig](config, "history"))
      keystore <- F.delay(ConfigLoader.loadOrThrow[KeyStoreConfig](config, "keystore"))
      peer     <- F.delay(ConfigLoader.loadOrThrow[PeerConfig](config, "peer"))
      sync     <- F.delay(ConfigLoader.loadOrThrow[SyncConfig](config, "sync"))
      txPool   <- F.delay(ConfigLoader.loadOrThrow[TxPoolConfig](config, "txPool"))
      mining   <- F.delay(ConfigLoader.loadOrThrow[MiningConfig](config, "mining"))
      rpc      <- F.delay(ConfigLoader.loadOrThrow[RpcConfig](config, "rpc"))
    } yield
      FullNodeConfig(
        datadir,
        identity,
        genesis,
        history,
        keystore,
        peer,
        sync,
        txPool,
        mining,
        rpc
      )

  implicit private val bigIntReader: ConfigReader[BigInt] = ConfigReader.fromString[BigInt](
    ConvertHelpers.catchReadError(s => BigInt(s))
  )

  implicit private val uint256Reader: ConfigReader[UInt256] = ConfigReader.fromString[UInt256](
    ConvertHelpers.catchReadError(s => UInt256(s.toInt))
  )

  implicit private val bytevectorReader: ConfigReader[ByteVector] = ConfigReader.fromString[ByteVector](
    ConvertHelpers.catchReadError(s => ByteVector.fromValidHex(s))
  )

  implicit private val hint: ProductHint[GenesisConfig] =
    ProductHint[GenesisConfig](fieldMapping = ConfigFieldMapping(CamelCase, CamelCase), allowUnknownKeys = false)

  private def loadOrThrow[A: ClassTag](config: Config, namespace: String)(
      implicit reader: Derivation[ConfigReader[A]]): A =
    pureconfig.loadConfigOrThrow[A](config, namespace)
}
