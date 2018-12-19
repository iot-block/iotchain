package jbok.core.config

import java.net.InetSocketAddress

import cats.effect.IO
import jbok.core.models.{Address, UInt256}
import jbok.core.peer.PeerNode
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits._

import scala.concurrent.duration._

object Configs {
  case class FullNodeConfig(
      datadir: String,
      identity: String,
      logLevel: String,
      genesisOrPath: Either[GenesisConfig, String],
      history: HistoryConfig,
      keystore: KeyStoreConfig,
      peer: PeerConfig,
      sync: SyncConfig,
      txPool: TxPoolConfig,
      mining: MiningConfig,
      rpc: RpcConfig
  ) {
    def genesis = genesisOrPath match {
      case Left(g)     => g
      case Right(path) => GenesisConfig.fromFile(path).unsafeRunSync()
    }

    def logsDir: String = s"${datadir}/logs"

    def lockPath: String = s"${datadir}/LOCK"

    def genesisPath: String = s"${datadir}/genesis.conf"

    def withGenesis(f: GenesisConfig => GenesisConfig): FullNodeConfig =
      copy(genesisOrPath = Left(f(genesis)))

    def withGenesisPath(path: String): FullNodeConfig =
      copy(genesisOrPath = Right(path))

    def withPeer(f: PeerConfig => PeerConfig): FullNodeConfig =
      copy(peer = f(peer))

    def withSync(f: SyncConfig => SyncConfig): FullNodeConfig =
      copy(sync = f(sync))

    def withTxPool(f: TxPoolConfig => TxPoolConfig): FullNodeConfig =
      copy(txPool = f(txPool))

    def withRpc(f: RpcConfig => RpcConfig): FullNodeConfig =
      copy(rpc = f(rpc))

    def withMining(f: MiningConfig => MiningConfig): FullNodeConfig =
      copy(mining = f(mining))

    def withIdentityAndPort(identity: String, port: Int): FullNodeConfig =
      copy(identity = identity)
        .withPeer(
          _.copy(
            port = port,
            nodekeyOrPath = Left(Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()),
            discoveryPort = port + 1
          )
        )
        .withRpc(
          _.copy(enabled = true, port = port + 2)
        )
  }

  object FullNodeConfig {
    def fill(template: FullNodeConfig, size: Int): List[FullNodeConfig] =
      (0 until size).toList.map(i => {
        template.withIdentityAndPort(s"test-node-${i}", 10000 + { i * 3 })
      })
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
      nodekeyOrPath: Either[KeyPair, String],
      enableDiscovery: Boolean,
      discoveryPort: Int,
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
    val bootNodes: List[PeerNode]        = bootUris.flatMap(s => PeerNode.fromStr(s).toOption)
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
      minerAddressOrKey: Either[Address, KeyPair],
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
