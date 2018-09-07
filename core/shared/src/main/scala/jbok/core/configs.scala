package jbok.core

import java.util.UUID

import better.files.File._
import better.files._
import com.typesafe.config.{Config, ConfigFactory}
import jbok.core.models.{Address, UInt256}
import jbok.network.NetAddress
import pureconfig.{ConfigReader, Derivation}
import scodec.bits._

import scala.concurrent.duration._

object Configs {
  lazy val defaultConfig = ConfigFactory.load()

  implicit val bytesReader: ConfigReader[ByteVector] = ConfigReader[String].map(x => ByteVector.fromValidHex(x))

  implicit val byteReader: ConfigReader[Byte] = ConfigReader[Int].map(x => x.toByte)

  implicit val bigIntReader: ConfigReader[BigInt] = ConfigReader[String].map(x => BigInt(x))

  def loadConfig[A](namespace: String, config: Config = defaultConfig)(implicit ev: Derivation[ConfigReader[A]]) =
    pureconfig.loadConfig[A](config, namespace)

  val defaultRootDir: File = home / ".jbok"

  case class NetworkConfig(
      rpcBindAddress: NetAddress,
      peerBindAddress: NetAddress
  )

  case class KeyStoreConfig(
      keystoreDir: String = (defaultRootDir / "keystore").pathAsString
  )

  case class PeerManagerConfig(
      bindAddr: NetAddress,
      updatePeersInterval: FiniteDuration = 10.seconds,
      maxOutgoingPeers: Int = 10,
      maxIncomingPeers: Int = 10,
      maxPendingPeers: Int = 10,
      connectionTimeout: FiniteDuration = 5.seconds,
      handshakeTimeout: FiniteDuration = 5.seconds,
      timeout: FiniteDuration = 5.seconds
  )

  case class FullNodeConfig(
      rootDir: String = defaultRootDir.pathAsString,
      network: NetworkConfig,
      keystore: KeyStoreConfig,
      peer: PeerManagerConfig,
      blockChainConfig: BlockChainConfig,
      daoForkConfig: DaoForkConfig,
      miningConfig: MiningConfig,
      nodeId: String = UUID.randomUUID().toString
  )

  case class BlockChainConfig(
      frontierBlockNumber: BigInt = 0,
      homesteadBlockNumber: BigInt = 1150000,
      eip150BlockNumber: BigInt = BigInt("2500000"),
      eip155BlockNumber: BigInt = BigInt("3000000"),
      eip160BlockNumber: BigInt = BigInt("3000000"),
      eip161BlockNumber: BigInt = BigInt("1000000000000000000"),
      maxCodeSize: Option[BigInt] = None,
      difficultyBombPauseBlockNumber: BigInt = BigInt("3000000"),
      difficultyBombContinueBlockNumber: BigInt = BigInt("5000000"),
      customGenesisFileOpt: Option[String] = None,
      daoForkConfig: Option[DaoForkConfig] = None,
      accountStartNonce: UInt256 = UInt256.Zero,
      chainId: Byte = 0x3d.toByte,
      monetaryPolicyConfig: MonetaryPolicyConfig = MonetaryPolicyConfig(),
      gasTieBreaker: Boolean = false
  )

  case class DaoForkConfig(
      forkBlockNumber: BigInt = BigInt("1920000"),
      forkBlockHash: ByteVector = hex"94365e3a8c0b35089c1d1195081fe7489b528a84b22199c916180db8b28ade7f",
      blockExtraData: Option[ByteVector] = None,
      range: Int = 10,
      refundContract: Option[Address] = None,
      drainList: List[Address] = Nil
  ) {
    private lazy val extraDataBlockRange = forkBlockNumber until (forkBlockNumber + range)

    def isDaoForkBlock(blockNumber: BigInt): Boolean = forkBlockNumber == blockNumber

    def requiresExtraData(blockNumber: BigInt): Boolean =
      blockExtraData.isDefined && (extraDataBlockRange contains blockNumber)

    def getExtraData(blockNumber: BigInt): Option[ByteVector] =
      if (requiresExtraData(blockNumber)) blockExtraData
      else None
  }

  case class MonetaryPolicyConfig(
      eraDuration: Int = 5000000,
      rewardReductionRate: Double = 0.2,
      firstEraBlockReward: BigInt = BigInt("5000000000000000000")
  )

  case class MiningConfig(
      ommersPoolSize: Int = 30,
      blockCacheSize: Int = 30,
      coinbase: Address = Address(42),
      activeTimeout: FiniteDuration = 5.seconds,
      ommerPoolQueryTimeout: FiniteDuration = 5.seconds,
      headerExtraData: ByteVector = ByteVector("jbok".getBytes),
      miningEnabled: Boolean = false,
      ethashDir: String = "~/.ethash",
      mineRounds: Int = 100000
  )

  case class FilterConfig(
      filterTimeout: FiniteDuration = 10.minutes,
      filterManagerQueryTimeout: FiniteDuration = 3.minutes
  )

  case class SyncConfig(
      blacklistDuration: FiniteDuration,
      startRetryInterval: FiniteDuration,
      printStatusInterval: FiniteDuration,
      maxConcurrentRequests: Int,
      blockHeadersPerRequest: Int,
      blockBodiesPerRequest: Int,
      receiptsPerRequest: Int,
      nodesPerRequest: Int,
      minPeersToChooseTargetBlock: Int,
      targetBlockOffset: Int,
      blockChainOnlyPeersPoolSize: Int
  )

  case class Timeouts(
      shortTimeout: FiniteDuration = 500.millis,
      normalTimeout: FiniteDuration = 3.seconds,
      longTimeout: FiniteDuration = 10.seconds,
      veryLongTimeout: FiniteDuration = 30.seconds
  )

  object FullNodeConfig {
    def apply(suffix: String, port: Int): FullNodeConfig = {
      val rootDir                            = home / ".jbok" / suffix
      val networkConfig                      = NetworkConfig(NetAddress("localhost", port), NetAddress("localhost", port))
      val walletConfig                       = KeyStoreConfig((rootDir / "keystore").pathAsString)
      val peerManagerConfig                  = PeerManagerConfig(NetAddress("localhost", port))
      val blockChainConfig: BlockChainConfig = BlockChainConfig()
      val daoForkConfig: DaoForkConfig       = DaoForkConfig()
      val miningConfig                       = MiningConfig()
      FullNodeConfig(
        rootDir.pathAsString,
        networkConfig,
        walletConfig,
        peerManagerConfig,
        blockChainConfig,
        daoForkConfig,
        miningConfig
      )
    }

    def fill(size: Int): List[FullNodeConfig] =
      (0 until size).toList.map(i => {
        FullNodeConfig(s"test-${10000 + i}", 10000 + i)
      })
  }
}
