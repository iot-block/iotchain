package jbok.core.config

import java.net.InetSocketAddress

import jbok.core.models.{Address, UInt256}
import jbok.core.peer.PeerNode
import scodec.bits._

import scala.concurrent.duration._

object Configs {
  val home = System.getProperty("user.home")

  val defaultRootDir: String = s"${home}/.jbok"

  case class FullNodeConfig(
      datadir: String,
      keystore: KeyStoreConfig,
      peer: PeerManagerConfig,
      sync: SyncConfig,
      txPool: TxPoolConfig,
      mining: MiningConfig,
      rpc: RpcConfig,
      blockchain: BlockChainConfig,
      daofork: DaoForkConfig
  )

  object FullNodeConfig {
    def apply(suffix: String, port: Int): FullNodeConfig = {
      val datadir                      = s"${defaultRootDir}/${suffix}"
      val keystore                     = KeyStoreConfig(s"${datadir}/keystore")
      val peer                         = PeerManagerConfig(port, timeout = 0.seconds)
      val sync                         = SyncConfig()
      val txPool                       = TxPoolConfig()
      val mining                       = MiningConfig()
      val rpc                          = RpcConfig(true, "localhost", port + 100)
      val blockchain: BlockChainConfig = BlockChainConfig()
      val daofork: DaoForkConfig       = DaoForkConfig()
      FullNodeConfig(
        datadir,
        keystore,
        peer,
        sync,
        txPool,
        mining,
        rpc,
        blockchain,
        daofork,
      )
    }

    def fill(size: Int): List[FullNodeConfig] =
      (0 until size).toList.map(i => {
        FullNodeConfig(s"test-${10000 + i}", 10000 + i)
      })
  }

  case class RpcConfig(
      enabled: Boolean = false,
      host: String,
      port: Int
  ) {
    val addr = new InetSocketAddress(host, port)
  }

  case class KeyStoreConfig(
      keystoreDir: String = s"${defaultRootDir}/keystore"
  )

  case class PeerManagerConfig(
      port: Int = 10000,
      host: String = "localhost",
      nodekeyPath: String = s"${defaultRootDir}/nodekey",
      bootUris: List[String] = Nil,
      updatePeersInterval: FiniteDuration = 10.seconds,
      maxOutgoingPeers: Int = 10,
      maxIncomingPeers: Int = 10,
      maxPendingPeers: Int = 10,
      connectionTimeout: FiniteDuration = 10.seconds,
      handshakeTimeout: FiniteDuration = 10.seconds,
      timeout: FiniteDuration = 10.seconds
  ) {
    val bindAddr: InetSocketAddress = new InetSocketAddress(host, port)
    val bootNodes                   = bootUris.flatMap(s => PeerNode.fromStr(s).toOption)
  }

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
      maxConcurrentRequests: Int = 50,
      maxBlockHeadersPerRequest: Int = 200,
      maxBlockBodiesPerRequest: Int = 128,
      maxReceiptsPerRequest: Int = 60,
      maxNodesPerRequest: Int = 200,
      minPeersToChooseTargetBlock: Int = 2,
      minBroadcastPeers: Int = 4,
      targetBlockOffset: Int = 500,
      retryInterval: FiniteDuration = 5.seconds,
      checkForNewBlockInterval: FiniteDuration = 5.seconds,
      banDuration: FiniteDuration = 200.seconds
  )

  case class DiscoveryConfig(
      enabled: Boolean = true,
      interface: String = "localhost",
      port: Int = 8889,
      bootstrapNodes: Set[PeerNode] = Set.empty,
      nodesLimit: Int = 10,
      scanMaxNodes: Int = 10,
      scanInterval: FiniteDuration = 10.seconds,
      messageExpiration: FiniteDuration = 10.seconds
  )
}
