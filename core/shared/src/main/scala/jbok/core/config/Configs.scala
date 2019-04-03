package jbok.core.config

import java.net.InetSocketAddress

import jbok.core.models.{Address, UInt256}
import jbok.core.peer.PeerNode
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits._
import _root_.io.circe.syntax._
import _root_.io.circe.parser._
import _root_.io.circe.generic.auto._
import better.files.{DefaultCharset, File}
import cats.effect.IO

import scala.concurrent.duration._
import jbok.codec.json.implicits._

import scala.io.Source

object Configs {
  final case class CoreConfig(
      identity: String,
      logLevel: String,
      logHandler: String,
      genesisConfig: Option[GenesisConfig],
      history: HistoryConfig,
      keystore: KeyStoreConfig,
      peer: PeerConfig,
      sync: SyncConfig,
      txPool: TxPoolConfig,
      mining: MiningConfig,
      rpc: RpcConfig,
      consensusAlgo: String
  ) {
    val dataDir: String = {
      val home = System.getProperty("user.home")
      s"${home}/.jbok/${identity}"
    }

    val keystoreDir: String =
      s"${dataDir}/keystore"

    val chainDataDir: String =
      s"${dataDir}/chainData"

    val nodeKeyPath: String =
      s"${dataDir}/nodekey"

    val logDir: String = s"${dataDir}/logs"

    val lockPath: String = s"${dataDir}/LOCK"

    val genesisPath: String = s"${dataDir}/genesis.json"

    lazy val genesis: GenesisConfig = genesisConfig match {
      case Some(g) => g
      case None    => GenesisConfig.fromFile(genesisPath).unsafeRunSync()
    }

    lazy val nodeKeyPair: KeyPair = peer.nodekey.getOrElse(PeerConfig.loadOrGenerateNodeKey(nodeKeyPath))

    lazy val peerNode: PeerNode = PeerNode(nodeKeyPair.public, peer.host, peer.port, peer.discoveryPort)

    def withIdentityAndPort(identity: String, port: Int): CoreConfig =
      copy(identity = identity)
        .withPeer(_.copy(port = port, discoveryPort = port + 1))
        .withRpc(_.copy(port = port + 2))

    def withHistory(f: HistoryConfig => HistoryConfig): CoreConfig =
      copy(history = f(history))

    def withKeyStore(f: KeyStoreConfig => KeyStoreConfig): CoreConfig =
      copy(keystore = f(keystore))

    def withPeer(f: PeerConfig => PeerConfig): CoreConfig =
      copy(peer = f(peer))

    def withSync(f: SyncConfig => SyncConfig): CoreConfig =
      copy(sync = f(sync))

    def withTxPool(f: TxPoolConfig => TxPoolConfig): CoreConfig =
      copy(txPool = f(txPool))

    def withMining(f: MiningConfig => MiningConfig): CoreConfig =
      copy(mining = f(mining))

    def withRpc(f: RpcConfig => RpcConfig): CoreConfig =
      copy(rpc = f(rpc))

    def toJson: String =
      this.asJson.spaces2
  }

  final case class KeyStoreConfig(
      dbBackend: String
  )

  final case class HistoryConfig(
      dbBackend: String,
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
      nodekey: Option[KeyPair],
      enableDiscovery: Boolean,
      discoveryPort: Int,
      dbBackend: String,
      bootUris: List[String],
      updatePeersInterval: FiniteDuration,
      maxOutgoingPeers: Int,
      maxIncomingPeers: Int,
      maxPendingPeers: Int,
      timeout: FiniteDuration
  ) {
    val bindAddr: InetSocketAddress      = new InetSocketAddress(host, port)
    val discoveryAddr: InetSocketAddress = new InetSocketAddress(host, discoveryPort)
    val bootNodes: List[PeerNode]        = bootUris.flatMap(s => PeerNode.fromStr(s).toOption.toList)
  }

  object PeerConfig {
    def loadNodeKey(path: String): KeyPair = {
      val line   = File(path).lines(DefaultCharset).headOption.getOrElse("")
      val secret = KeyPair.Secret(line)
      val pubkey = Signature[ECDSA].generatePublicKey[IO](secret).unsafeRunSync()
      KeyPair(pubkey, secret)
    }

    def saveNodeKey(path: String, keyPair: KeyPair): IO[Unit] =
      IO(File(path).createIfNotExists(createParents = true).overwrite(keyPair.secret.bytes.toHex))

    def loadOrGenerateNodeKey(path: String): KeyPair =
      IO(loadNodeKey(path)).attempt
        .flatMap {
          case Left(e) =>
            for {
              keyPair <- Signature[ECDSA].generateKeyPair[IO]()
              _       <- saveNodeKey(path, keyPair)
            } yield keyPair

          case Right(nodeKey) =>
            IO.pure(nodeKey)
        }
        .unsafeRunSync()
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
      minerKeyPair: Option[KeyPair],
      minerAddress: Address,
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

  object CoreConfig {
    def fromJson(json: String): CoreConfig =
      decode[CoreConfig](json) match {
        case Left(e)  => throw e
        case Right(c) => c
      }

    val reference: CoreConfig =
      CoreConfig.fromJson(
        Source.fromInputStream(this.getClass.getResourceAsStream("/reference.json")).getLines().mkString("\n"))
  }
}
