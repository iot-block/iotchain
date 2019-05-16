package jbok.core.config

import java.net.InetSocketAddress

import jbok.core.models.{Address, UInt256}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits._
import better.files.{DefaultCharset, File}
import cats.effect.IO
import io.circe.generic.JsonCodec

import scala.concurrent.duration._
import jbok.codec.json.implicits._
import jbok.common.log.LogConfig
import jbok.core.peer.PeerUri
import monocle.macros.Lenses

object Configs {
  @JsonCodec
  @Lenses
  final case class CoreConfig(
      identity: String,
      log: LogConfig,
      genesis: GenesisConfig,
      history: HistoryConfig,
      keystore: KeyStoreConfig,
      peer: PeerConfig,
      sync: SyncConfig,
      txPool: TxPoolConfig,
      blockPool: BlockPoolConfig,
      ommerPool: OmmerPoolConfig,
      mining: MiningConfig,
      rpc: RpcConfig
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

//    lazy val nodeKeyPair: KeyPair = peer.nodekey.getOrElse(PeerConfig.loadOrGenerateNodeKey(nodeKeyPath))
//
//    lazy val peerUri: PeerUri = PeerUri.fromTcpAddr(nodeKeyPair.public, new InetSocketAddress(peer.host, peer.port))
  }

  @JsonCodec
  final case class KeyStoreConfig(
      dbBackend: String
  )

  @JsonCodec
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

  @JsonCodec
  final case class PeerConfig(
      host: String,
      port: Int,
      secret: String,
      dbBackend: String,
      seeds: List[String],
      updatePeersInterval: FiniteDuration,
      maxOutgoingPeers: Int,
      maxIncomingPeers: Int,
      maxBufferSize: Int,
      timeout: FiniteDuration
  ) {
    val bindAddr: InetSocketAddress = new InetSocketAddress(host, port)
    val seedUris: List[PeerUri]     = seeds.flatMap(s => PeerUri.fromStr(s).toOption.toList)
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

  @JsonCodec
  final case class RpcConfig(
      enabled: Boolean,
      host: String,
      port: Int,
      apis: String
  ) {
    val rpcAddr = new InetSocketAddress(host, port)
  }

  @JsonCodec
  final case class MonetaryPolicyConfig(
      eraDuration: Int = 5000000,
      rewardReductionRate: Double = 0.2,
      firstEraBlockReward: BigInt = BigInt("5000000000000000000")
  )

  @JsonCodec
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
      checkpointInterval: Int = 1024,
      minBroadcastPeers: Int = 4
  )

  @JsonCodec
  final case class TxPoolConfig(
      poolSize: Int = 4096,
      transactionTimeout: FiniteDuration = 10.minutes
  )

  @JsonCodec
  final case class OmmerPoolConfig(
      poolSize: Int,
      ommerGenerationLimit: Int = 6,
      ommerSizeLimit: Int = 2
  )

  @JsonCodec
  final case class BlockPoolConfig(
      maxBlockAhead: Int,
      maxBlockBehind: Int
  )

  @JsonCodec
  final case class SyncConfig(
      host: String,
      port: Int,
      maxConcurrentRequests: Int,
      maxBlockHeadersPerRequest: Int,
      maxBlockBodiesPerRequest: Int,
      minPeersToChooseTargetBlock: Int,
      fullSyncOffset: Int,
      retryInterval: FiniteDuration,
      checkForNewBlockInterval: FiniteDuration,
      banDuration: FiniteDuration,
      requestTimeout: FiniteDuration
  ) {
    val syncAddr: InetSocketAddress = new InetSocketAddress(host, port)
  }

  @JsonCodec
  final case class TcpConfig(
      readTimeout: FiniteDuration,
      writeTimeout: FiniteDuration,
      bindHost: String,
      bindPort: Int,
      maxBufferSize: Int = 4 * 1024 * 1024
  )
}
