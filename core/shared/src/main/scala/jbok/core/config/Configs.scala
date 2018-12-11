package jbok.core.config

import java.net.InetSocketAddress

import jbok.core.models.{Address, UInt256}
import jbok.persistent.KeyValueDB
import scodec.bits._

import scala.concurrent.duration._

object Configs {
  val home: String = System.getProperty("user.home")

  val defaultRootDir: String = s"${home}/.jbok"

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
    val keystoreDir = s"${datadir}/keystore"
  }

  object FullNodeConfig {
    def apply(identity: String, port: Int): FullNodeConfig = {
      val datadir  = s"${defaultRootDir}/${identity}"
      val genesis = GenesisConfig.default
      val history  = HistoryConfig()
      val keystore = KeyStoreConfig(s"${datadir}/keystore")
      val peer     = PeerConfig(port)
      val sync     = SyncConfig()
      val txPool   = TxPoolConfig()
      val mining   = MiningConfig(enabled = false)
      val rpc      = RpcConfig(enabled = false, "localhost", port + 200, "public,admin")

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
    }

    def fill(size: Int): List[FullNodeConfig] =
      (0 until size).toList.map(i => {
        FullNodeConfig(s"test-${10000 + i}", 10000 + i)
      })
  }

  case class KeyStoreConfig(
      keystoreDir: String = s"${defaultRootDir}/keystore"
  )

  case class HistoryConfig(
      chainDataDir: String = KeyValueDB.INMEM,
      frontierBlockNumber: BigInt = 0,
      homesteadBlockNumber: BigInt = 1150000,
      tangerineWhistleBlockNumber: BigInt = 2463000,
      spuriousDragonBlockNumber: BigInt = 2675000,
      byzantiumBlockNumber: BigInt = 4370000,
      constantinopleBlockNumber: BigInt = BigInt("1000000000000000000000"), // TBD on the Ethereum mainnet
      maxCodeSize: Option[BigInt] = Some(24 * 1024),
      difficultyBombPauseBlockNumber: BigInt = BigInt("3000000"),
      difficultyBombContinueBlockNumber: BigInt = BigInt("5000000"),
      customGenesisFileOpt: Option[String] = None,
      accountStartNonce: UInt256 = UInt256.Zero,
      monetaryPolicyConfig: MonetaryPolicyConfig = MonetaryPolicyConfig(),
      gasTieBreaker: Boolean = false
  )

  case class PeerConfig(
      port: Int = 30314,
      host: String = "localhost",
      enableDiscovery: Boolean = false,
      discoveryPort: Int = 30315,
      peerDataDir: String = s"${defaultRootDir}/peerData",
      nodekeyPath: String = s"${defaultRootDir}/nodekey",
      bootUris: List[String] = Nil,
      updatePeersInterval: FiniteDuration = 10.seconds,
      maxOutgoingPeers: Int = 10,
      maxIncomingPeers: Int = 10,
      maxPendingPeers: Int = 10,
      handshakeTimeout: FiniteDuration = 10.seconds,
      timeout: FiniteDuration = 10.seconds
  ) {
    val bindAddr: InetSocketAddress      = new InetSocketAddress(host, port)
    val discoveryAddr: InetSocketAddress = new InetSocketAddress(host, discoveryPort)
  }

  case class RpcConfig(
      enabled: Boolean = false,
      host: String = "localhost",
      port: Int = 30316,
      apis: String = "admin,public"
  ) {
    val addr = new InetSocketAddress(host, port)
  }

  case class MonetaryPolicyConfig(
      eraDuration: Int = 5000000,
      rewardReductionRate: Double = 0.2,
      firstEraBlockReward: BigInt = BigInt("5000000000000000000")
  )

  case class MiningConfig(
      enabled: Boolean = false,
      ommersPoolSize: Int = 30,
      blockCacheSize: Int = 30,
      coinbaseStr: String = Address(42).bytes.toHex,
      activeTimeout: FiniteDuration = 5.seconds,
      headerExtraDataStr: String = ByteVector("jbok".getBytes).toHex,
      ethashDir: String = "~/.ethash",
      mineRounds: Int = 100000
  ) {
    val coinbase: Address = Address(ByteVector(coinbaseStr.getBytes))

    val headerExtraData: ByteVector = ByteVector(headerExtraDataStr.getBytes)
  }

  final case class TxPoolConfig(
      poolSize: Int = 4096,
      transactionTimeout: FiniteDuration = 10.minutes
  )

  case class SyncConfig(
      maxConcurrentRequests: Int = 50, // fast sync
      maxBlockHeadersPerRequest: Int = 128, // fast/full sync
      maxBlockBodiesPerRequest: Int = 128, // fast/full sync
      maxReceiptsPerRequest: Int = 60, // fast sync
      maxNodesPerRequest: Int = 200, // fast sync
      minPeersToChooseTargetBlock: Int = 2, // fast sync
      minBroadcastPeers: Int = 4,
      fullSyncOffset: Int = 10, // the actual full sync number = min(1, current + 1 - offset)
      fastSyncOffset: Int = 64, // fast sync
      fastEnabled: Boolean = false,
      retryInterval: FiniteDuration = 15.seconds,
      checkForNewBlockInterval: FiniteDuration = 5.seconds,
      banDuration: FiniteDuration = 200.seconds,
      requestTimeout: FiniteDuration = 10.seconds,
  )
}
