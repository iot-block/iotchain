package jbok.core.config

import java.net.InetSocketAddress

import jbok.core.models.{Address, UInt256}
import jbok.core.peer.PeerNode
import jbok.crypto.signature.KeyPair
import scodec.bits._

import scala.concurrent.duration._

object Configs {
  final case class FullNodeConfig(
      rootDir: String,
      identity: String,
      dataDir: String,
      logLevel: String,
      logDir: String,
      genesisOrPath: Either[GenesisConfig, String],
      history: HistoryConfig,
      keystore: KeyStoreConfig,
      peer: PeerConfig,
      sync: SyncConfig,
      txPool: TxPoolConfig,
      mining: MiningConfig,
      rpc: RpcConfig,
      consensusAlgo: String = "clique"
  ) {
    lazy val genesis: GenesisConfig = genesisOrPath match {
      case Left(g)     => g
      case Right(path) => GenesisConfig.fromFile(path).unsafeRunSync()
    }

    lazy val lockPath: String = s"${dataDir}/LOCK"

    lazy val genesisPath: String = s"${dataDir}/genesis.conf"

    def withGenesis(f: GenesisConfig => GenesisConfig): FullNodeConfig =
      copy(genesisOrPath = Left(f(genesis)))

    def withHistory(f: HistoryConfig => HistoryConfig): FullNodeConfig =
      copy(history = f(history))

    def withKeyStore(f: KeyStoreConfig => KeyStoreConfig): FullNodeConfig =
      copy(keystore = f(keystore))

    def withPeer(f: PeerConfig => PeerConfig): FullNodeConfig =
      copy(peer = f(peer))

    def withSync(f: SyncConfig => SyncConfig): FullNodeConfig =
      copy(sync = f(sync))

    def withTxPool(f: TxPoolConfig => TxPoolConfig): FullNodeConfig =
      copy(txPool = f(txPool))

    def withMining(f: MiningConfig => MiningConfig): FullNodeConfig =
      copy(mining = f(mining))

    def withRpc(f: RpcConfig => RpcConfig): FullNodeConfig =
      copy(rpc = f(rpc))
  }

  final case class KeyStoreConfig(
      dbBackend: String,
      keystoreDir: String
  )

  final case class HistoryConfig(
      dbBackend: String,
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

  final case class PeerConfig(
      port: Int,
      host: String,
      nodekeyOrPath: Either[KeyPair, String],
      enableDiscovery: Boolean,
      discoveryPort: Int,
      dbBackend: String,
      peerDataDir: String,
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
    val bootNodes: List[PeerNode]        = bootUris.flatMap(s => PeerNode.fromStr(s).toOption.toList)
  }

  final case class RpcConfig(
      enabled: Boolean,
      host: String,
      port: Int,
      apis: String
  ) {
    val addr = new InetSocketAddress(host, port)
  }

  final case class MonetaryPolicyConfig(
      eraDuration: Int = 5000000,
      rewardReductionRate: Double = 0.2,
      firstEraBlockReward: BigInt = BigInt("5000000000000000000")
  )

  final case class MiningConfig(
      enabled: Boolean,
      ommersPoolSize: Int,
      blockCacheSize: Int,
      minerAddressOrKey: Either[Address, KeyPair],
      coinbase: Address,
      extraData: ByteVector,
      mineRounds: Int,
      period: FiniteDuration = 15.seconds,
      epoch: BigInt = BigInt("30000"),
      checkpointInterval: Int = 1024
  )

  final case class TxPoolConfig(
      poolSize: Int = 4096,
      transactionTimeout: FiniteDuration = 10.minutes
  )

  final case class SyncConfig(
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
