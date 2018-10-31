package jbok.app

import java.net.InetSocketAddress
import java.security.SecureRandom

import cats.effect._
import cats.implicits._
import jbok.app.api.FilterManager
import jbok.app.api.impl.{PrivateApiImpl, PublicApiImpl}
import jbok.core.History
import jbok.core.config.Configs.{FilterConfig, FullNodeConfig, SyncConfig}
import jbok.core.consensus.Consensus
import jbok.core.consensus.poa.clique.{Clique, CliqueConfig, CliqueConsensus}
import jbok.core.keystore.{KeyStore, KeyStorePlatform}
import jbok.core.ledger.BlockExecutor
import jbok.core.mining.BlockMiner
import jbok.core.models.Address
import jbok.core.peer.PeerManagerPlatform
import jbok.core.pool.{BlockPool, BlockPoolConfig, OmmerPool, TxPool}
import jbok.core.sync.{Broadcaster, Synchronizer}
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.network.rpc.RpcServer
import jbok.network.rpc.RpcServer._
import jbok.network.server.Server
import jbok.persistent.leveldb.{LevelDB, LevelDBConfig}
import scodec.bits.ByteVector

case class FullNode[F[_]](
    config: FullNodeConfig,
    miner: BlockMiner[F],
    keyStore: KeyStore[F],
    rpc: RpcServer,
    server: Server[F],
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  val synchronizer = miner.synchronizer
  val peerManager  = synchronizer.peerManager
  val txPool       = synchronizer.txPool
  val keyPair      = peerManager.keyPair
  val peerNode     = peerManager.peerNode
  val id           = peerNode.id.toHex

  val peerBindAddress: InetSocketAddress =
    config.peer.bindAddr

  def start: F[Unit] =
    for {
      _ <- peerManager.start
      _ <- synchronizer.start
      _ <- txPool.start
      _ <- if (config.rpc.enabled) server.start else F.unit
      _ <- if (config.mining.enabled) miner.start else F.unit
    } yield ()

  def stop: F[Unit] =
    for {
      _ <- txPool.stop
      _ <- synchronizer.stop
      _ <- server.stop
      _ <- miner.stop
      _ <- peerManager.stop
    } yield ()
}

object FullNode {
  import jbok.common.execution._
  def forConfig(config: FullNodeConfig)(
      implicit F: ConcurrentEffect[IO],
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[FullNode[IO]] = {
    val random = new SecureRandom()
    for {
      db <- LevelDB(LevelDBConfig(s"${config.datadir}/db"))
      keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()
      history <- History(db)
      // load genesis if does not exist
      _         <- history.loadGenesisConfig()
      blockPool <- BlockPool(history, BlockPoolConfig())
      sign      = (bv: ByteVector) => { SecP256k1.sign(bv.toArray, keyPair) }
      clique    = Clique(CliqueConfig(), history, Address(keyPair), sign)
      consensus = new CliqueConsensus[IO](blockPool, clique)
      peerManager <- PeerManagerPlatform[IO](config.peer, Some(keyPair), SyncConfig(), history)
      executor = BlockExecutor[IO](config.blockchain, consensus)
      txPool    <- TxPool[IO](peerManager)
      ommerPool <- OmmerPool[IO](history)
      broadcaster = Broadcaster[IO](peerManager)
      synchronizer <- Synchronizer[IO](peerManager, executor, txPool, ommerPool, broadcaster)
      keyStore     <- KeyStorePlatform[IO](config.keystore.keystoreDir, random)
      miner        <- BlockMiner[IO](synchronizer)

      // mount rpc
      filterManager <- FilterManager.apply(miner, keyStore, FilterConfig())
      publicAPI <- PublicApiImpl(
        history,
        config.blockchain,
        config.mining,
        miner,
        keyStore,
        filterManager,
        1
      )

      privateAPI <- PrivateApiImpl(keyStore, history, config.blockchain, txPool)
      rpc        <- RpcServer().map(_.mountAPI(publicAPI).mountAPI(privateAPI))
      server     <- Server.websocket(config.rpc.addr, rpc.pipe)
    } yield FullNode[IO](config, miner, keyStore, rpc, server)
  }

  def apply(config: FullNodeConfig, consensus: Consensus[IO])(
      implicit F: ConcurrentEffect[IO],
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[FullNode[IO]] = {
    val random  = new SecureRandom()
    val keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()
    val history = consensus.history
    for {
      peerManager <- PeerManagerPlatform[IO](config.peer, Some(keyPair), SyncConfig(), history)
      executor = BlockExecutor[IO](config.blockchain, consensus)
      txPool    <- TxPool[IO](peerManager)
      ommerPool <- OmmerPool[IO](history)
      broadcaster = Broadcaster[IO](peerManager)
      synchronizer <- Synchronizer[IO](peerManager, executor, txPool, ommerPool, broadcaster)
      keyStore     <- KeyStorePlatform[IO](config.keystore.keystoreDir, random)
      miner        <- BlockMiner[IO](synchronizer)

      // mount rpc
      filterManager <- FilterManager.apply(miner, keyStore, FilterConfig())
      publicAPI <- PublicApiImpl(
        history,
        config.blockchain,
        config.mining,
        miner,
        keyStore,
        filterManager,
        1
      )

      privateAPI <- PrivateApiImpl(keyStore, history, config.blockchain, txPool)
      rpc        <- RpcServer().map(_.mountAPI(publicAPI).mountAPI(privateAPI))
      server     <- Server.websocket(config.rpc.addr, rpc.pipe)
    } yield FullNode[IO](config, miner, keyStore, rpc, server)
  }
}
