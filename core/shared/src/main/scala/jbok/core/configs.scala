package jbok.core

import java.util.UUID

import better.files.File._
import better.files._
import jbok.core.configs.FullNodeConfig
import jbok.core.models.{Address, UInt256}
import jbok.network.NetAddress
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.util.Random

package object configs {
  val defaultRootDir: File = home / ".jbok"

  case class NetworkConfig(
      rpcBindAddress: NetAddress,
      p2pBindAddress: NetAddress
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
      handshakeTimeout: FiniteDuration = 5.seconds
  )

  case class FullNodeConfig(
      rootDir: String = defaultRootDir.pathAsString,
      network: NetworkConfig,
      keystore: KeyStoreConfig,
      peer: PeerManagerConfig,
      blockChainConfig: BlockChainConfig,
      nodeId: String = UUID.randomUUID().toString
  )

  case class BlockChainConfig(
      maxCodeSize: Option[BigInt],
      customGenesisFileOpt: Option[String],
      accountStartNonce: UInt256,
      chainId: Byte
//      monetaryPolicyConfig: MonetaryPolicyConfig,
//      gasTieBreaker: Boolean
  )

  case class MonetaryPolicyConfig(
      eraDuration: Int,
      rewardRedutionRate: Double,
      firstEraBlockReward: BigInt
  )

  case class MiningConfig(
    ommersPoolSize: Int,
    blockCacheSize: Int,
    coinbase: Address,
    activeTimeout: FiniteDuration,
    ommerPoolQueryTimeout: FiniteDuration,
    headerExtraData: ByteVector,
    miningEnabled: Boolean,
    ethashDir: String,
    mineRounds: Int
  )

  object FullNodeConfig {
    def apply(suffix: String, port: Int): FullNodeConfig = {
      val rootDir = home / ".jbok" / suffix
      val networkConfig = NetworkConfig(NetAddress("localhost", port), NetAddress("localhost", port + 1))
      val walletConfig = KeyStoreConfig((rootDir / "keystore").pathAsString)
      val peerManagerConfig = PeerManagerConfig(NetAddress("localhost", port))
      val blockChainConfig: BlockChainConfig = ???
      FullNodeConfig(rootDir.pathAsString, networkConfig, walletConfig, peerManagerConfig, blockChainConfig)
    }

    def random(): FullNodeConfig = {
      val i = Random.nextInt(20000) + 1
      apply(s"test-${10000 + i}", 10000 + i)
    }
  }
}
