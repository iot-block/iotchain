package jbok.core.config

import java.net.InetSocketAddress

import jbok.core.models.{Address, UInt256}
import jbok.persistent.KeyValueDB
import scodec.bits._

import scala.concurrent.duration._

object Configs {
  case class FullNodeConfig(
      datadir: String,
      identity: String,
      genesis: GenesisConfig,
      history: HistoryConfig,
      keystore: KeyStoreConfig,
      peer: PeerConfig,
      sync: SyncConfig,
      txPool: TxPoolConfig,
      mining: MiningConfig,
      rpc: RpcConfig
  ) {
    val lock: String = s"${datadir}/LOCK"
  }

  case class KeyStoreConfig(
      keystoreDir: String
  )

  case class HistoryConfig(
      chainDataDir: String,
      frontierBlockNumber: BigInt = 0,
      homesteadBlockNumber: BigInt = 1150000,
      tangerineWhistleBlockNumber: BigInt = 2463000,
      spuriousDragonBlockNumber: BigInt = 2675000,
      byzantiumBlockNumber: BigInt = 4370000,
      constantinopleBlockNumber: BigInt = BigInt("1000000000000000000000"), // TBD on the Ethereum mainnet
      difficultyBombPauseBlockNumber: BigInt = BigInt("3000000"),
      difficultyBombContinueBlockNumber: BigInt = BigInt("5000000")
  ) {
    val accountStartNonce: UInt256                 = UInt256.Zero
    val maxCodeSize: Option[BigInt]                = Some(24 * 1024)
    val monetaryPolicyConfig: MonetaryPolicyConfig = MonetaryPolicyConfig()
  }

  case class PeerConfig(
      port: Int,
      host: String,
      enableDiscovery: Boolean,
      discoveryPort: Int,
      peerDataDir: String,
      nodekeyPath: String,
      bootUris: List[String],
      updatePeersInterval: FiniteDuration,
      maxOutgoingPeers: Int,
      maxIncomingPeers: Int,
      maxPendingPeers: Int,
      handshakeTimeout: FiniteDuration,
      timeout: FiniteDuration,
  ) {
    val bindAddr: InetSocketAddress      = new InetSocketAddress(host, port)
    val discoveryAddr: InetSocketAddress = new InetSocketAddress(host, discoveryPort)
  }

  case class RpcConfig(
      enabled: Boolean,
      host: String,
      port: Int,
      apis: String
  ) {
    val addr = new InetSocketAddress(host, port)
  }

  case class MonetaryPolicyConfig(
      eraDuration: Int = 5000000,
      rewardReductionRate: Double = 0.2,
      firstEraBlockReward: BigInt = BigInt("5000000000000000000")
  )

  case class MiningConfig(
      enabled: Boolean,
      ommersPoolSize: Int,
      blockCacheSize: Int,
      coinbase: Address,
      extraData: ByteVector,
      ethashDir: String,
      mineRounds: Int
  )

  final case class TxPoolConfig(
      poolSize: Int = 4096,
      transactionTimeout: FiniteDuration = 10.minutes
  )

  case class SyncConfig(
      maxConcurrentRequests: Int,
      maxBlockHeadersPerRequest: Int,
      maxBlockBodiesPerRequest: Int,
      maxReceiptsPerRequest: Int,
      maxNodesPerRequest: Int,
      minPeersToChooseTargetBlock: Int,
      minBroadcastPeers: Int,
      fullSyncOffset: Int,
      fastSyncOffset: Int,
      fastEnabled: Boolean,
      retryInterval: FiniteDuration,
      checkForNewBlockInterval: FiniteDuration,
      banDuration: FiniteDuration,
      requestTimeout: FiniteDuration
  )
}
