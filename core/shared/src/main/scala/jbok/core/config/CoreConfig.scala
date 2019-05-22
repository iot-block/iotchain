package jbok.core.config

import io.circe.generic.JsonCodec
import jbok.common.log.LogConfig
import jbok.codec.json.implicits._
//import jbok.crypto.ssl.SSLConfig
import jbok.persistent.PersistConfig

@JsonCodec
final case class CoreConfig(
    genesis: GenesisConfig,
    log: LogConfig,
    history: HistoryConfig,
    keystore: KeyStoreConfig,
    peer: PeerConfig,
    sync: SyncConfig,
    txPool: TxPoolConfig,
    blockPool: BlockPoolConfig,
    mining: MiningConfig,
    persist: PersistConfig,
//    ssl: SSLConfig,
    db: DatabaseConfig,
    service: ServiceConfig
)
object CoreConfig {}
