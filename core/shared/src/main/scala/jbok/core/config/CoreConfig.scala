package jbok.core.config

import io.circe.generic.JsonCodec
import jbok.common.log.LogConfig
import monocle.macros.Lenses
import jbok.codec.json.implicits._
import jbok.crypto.ssl.SSLConfig
import jbok.persistent.PersistConfig

@Lenses
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
    ssl: SSLConfig
) {
//  val dataDir: String = {
//    val home = System.getProperty("user.home")
//    s"${home}/.jbok/${identity}"
//  }
//
//  val keystoreDir: String =
//    s"${dataDir}/keystore"
//
//  val chainDataDir: String =
//    s"${dataDir}/chainData"
//
//  val nodeKeyPath: String =
//    s"${dataDir}/nodekey"
//
//  val logDir: String = s"${dataDir}/logs"
//
//  val lockPath: String = s"${dataDir}/LOCK"
//
//  val genesisPath: String = s"${dataDir}/genesis.json"

//    lazy val nodeKeyPair: KeyPair = peer.nodekey.getOrElse(PeerConfig.loadOrGenerateNodeKey(nodeKeyPath))
//
//    lazy val peerUri: PeerUri = PeerUri.fromTcpAddr(nodeKeyPair.public, new InetSocketAddress(peer.host, peer.port))
}

object CoreConfig {}

//  @JsonCodec
//  final case class MonetaryPolicyConfig(
//      eraDuration: Int = 5000000,
//      rewardReductionRate: Double = 0.2,
//      firstEraBlockReward: BigInt = BigInt("5000000000000000000")
//  )
