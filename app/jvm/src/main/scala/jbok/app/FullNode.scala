package jbok.app

import java.net.InetSocketAddress
import java.security.SecureRandom

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.app.api.impl.{PrivateApiImpl, PublicApiImpl}
import jbok.common.execution._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.consensus.Consensus
import jbok.core.consensus.poa.clique.{Clique, CliqueConfig, CliqueConsensus}
import jbok.core.keystore.{KeyStore, KeyStorePlatform}
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.mining.BlockMiner
import jbok.core.peer.PeerManagerPlatform
import jbok.core.pool.{BlockPool, BlockPoolConfig}
import jbok.core.sync.SyncManager
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.network.rpc.RpcServer
import jbok.network.rpc.RpcServer._
import jbok.network.server.Server
import jbok.persistent.leveldb.LevelDB

case class FullNode[F[_]](
    config: FullNodeConfig,
    syncManager: SyncManager[F],
    miner: BlockMiner[F],
    keyStore: KeyStore[F],
    rpc: RpcServer,
    server: Server[F],
    haltWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("FullNode")

  val executor    = syncManager.executor
  val history     = executor.history
  val peerManager = syncManager.peerManager
  val txPool      = executor.txPool
  val keyPair     = peerManager.keyPair
  val peerNode    = peerManager.peerNode
  val id          = peerNode.id.toHex

  val peerBindAddress: InetSocketAddress =
    config.peer.bindAddr

  def stream: Stream[F, Unit] =
    Stream.eval(haltWhenTrue.set(false)) ++
      Stream(
        peerManager.stream,
        syncManager.stream,
        server.stream,
        if (config.mining.enabled) miner.stream.drain else Stream.empty
      ).parJoinUnbounded
        .interruptWhen(haltWhenTrue)
        .handleErrorWith(e => Stream.eval(F.delay(log.warn(e)("FullNode error"))))
        .onFinalize(haltWhenTrue.set(true) *> F.delay(log.debug("FullNode finalized")))

  def start: F[Fiber[F, Unit]] =
    stream.compile.drain.start

  def stop: F[Unit] =
    haltWhenTrue.set(true)
}

object FullNode {
  def forConfig(config: FullNodeConfig)(
      implicit F: ConcurrentEffect[IO],
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[FullNode[IO]] = {
    val random = new SecureRandom()
    for {
      db <- LevelDB(s"${config.datadir}/db")
      keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()
      history <- History(db)
      // load genesis if does not exist
      _         <- history.init()
      blockPool <- BlockPool(history, BlockPoolConfig())
      clique    <- Clique(CliqueConfig(), history, keyPair)
      consensus = new CliqueConsensus[IO](clique, blockPool)
      peerManager <- PeerManagerPlatform[IO](config.peer, Some(keyPair), history)
      executor    <- BlockExecutor[IO](config.blockchain, consensus, peerManager)
      syncManager <- SyncManager(config.sync, executor)
      keyStore    <- KeyStorePlatform[IO](config.keystore.keystoreDir, random)
      miner       <- BlockMiner[IO](config.mining, syncManager)

      // mount rpc
      publicAPI <- PublicApiImpl(
        history,
        config.blockchain,
        config.mining,
        miner,
        keyStore,
        1
      )
      privateAPI   <- PrivateApiImpl(keyStore, history, config.blockchain, executor.txPool)
      rpc          <- RpcServer().map(_.mountAPI(publicAPI).mountAPI(privateAPI))
      server       <- Server.websocket(config.rpc.addr, rpc.pipe)
      haltWhenTrue <- SignallingRef[IO, Boolean](true)
    } yield FullNode[IO](config, syncManager, miner, keyStore, rpc, server, haltWhenTrue)
  }

  def forConfigAndConsensus(config: FullNodeConfig, consensus: Consensus[IO])(
      implicit F: ConcurrentEffect[IO],
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[FullNode[IO]] =
    for {
      nodeKey     <- Signature[ECDSA].generateKeyPair()
      peerManager <- PeerManagerPlatform[IO](config.peer, Some(nodeKey), consensus.history)
      executor    <- BlockExecutor[IO](config.blockchain, consensus, peerManager)
      syncManager <- SyncManager(config.sync, executor)
      keyStore    <- KeyStorePlatform[IO](config.keystore.keystoreDir, new SecureRandom())
      miner       <- BlockMiner[IO](config.mining, syncManager)

      // mount rpc
      publicAPI <- PublicApiImpl(
        consensus.history,
        config.blockchain,
        config.mining,
        miner,
        keyStore,
        1
      )
      privateAPI   <- PrivateApiImpl(keyStore, consensus.history, config.blockchain, executor.txPool)
      rpc          <- RpcServer().map(_.mountAPI(publicAPI).mountAPI(privateAPI))
      server       <- Server.websocket(config.rpc.addr, rpc.pipe)
      haltWhenTrue <- SignallingRef[IO, Boolean](true)
    } yield FullNode[IO](config, syncManager, miner, keyStore, rpc, server, haltWhenTrue)
}
