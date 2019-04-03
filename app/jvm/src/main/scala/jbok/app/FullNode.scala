package jbok.app

import java.net.InetSocketAddress
import java.nio.file.Paths

import cats.effect._
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.app.api.impl.{AdminApiImpl, PersonalApiImpl, PublicApiImpl}
import jbok.app.config.PeerNodeConfig
import jbok.app.service.ScanService
import jbok.codec.rlp.implicits._
import jbok.common.FileUtil
import jbok.common.execution._
import jbok.common.log.{Level, ScribeLog, ScribeLogPlatform}
import jbok.common.metrics.Metrics
import jbok.core.consensus.poa.clique.{Clique, CliqueConsensus}
import jbok.core.keystore.{KeyStore, KeyStorePlatform}
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.mining.BlockMiner
import jbok.core.peer.PeerManagerPlatform
import jbok.core.pool.{BlockPool, BlockPoolConfig}
import jbok.core.sync.SyncManager
import jbok.network.rpc.RpcService
import jbok.network.server.{Server, WsServer}

final case class FullNode(
    peerNodeConfig: PeerNodeConfig,
    syncManager: SyncManager[IO],
    miner: BlockMiner[IO],
    keyStore: KeyStore[IO],
    rpc: RpcService,
    server: Server[IO],
    haltWhenTrue: SignallingRef[IO, Boolean]
)(implicit F: ConcurrentEffect[IO], T: Timer[IO]) {
  private[this] val log = jbok.common.log.getLogger("FullNode")

  val coreConfig      = peerNodeConfig.core
  val executor    = syncManager.executor
  val history     = executor.history
  val peerManager = syncManager.peerManager
  val txPool      = executor.txPool
  val keyPair     = peerManager.keyPair
  val peerNode    = peerManager.peerNode
  val id          = peerNode.id.toHex

  val peerBindAddress: InetSocketAddress =
    coreConfig.peer.bindAddr

  def stream: Stream[IO, Unit] =
    Stream.eval(haltWhenTrue.set(false) >> F.delay(log.info(s"(${ coreConfig.identity}) start"))) ++
      Stream(
        peerManager.stream,
        syncManager.stream,
        if (coreConfig.rpc.enabled) server.stream else Stream.empty,
        if (coreConfig.mining.enabled) miner.stream.drain else Stream.empty,
        if (peerNodeConfig.service.enabled) ScanService.serve(peerNodeConfig.service) else Stream.empty
      ).parJoinUnbounded
        .interruptWhen(haltWhenTrue)
        .handleErrorWith(e => Stream.eval(F.delay(log.warn("FullNode error", e))))
        .onFinalize(haltWhenTrue.set(true) >> F.delay(log.info(s"(${coreConfig.identity}) ready to exit, bye bye...")))

  def start: IO[Fiber[IO, Unit]] =
    stream.compile.drain.start

  def stop: IO[Unit] =
    haltWhenTrue.set(true)
}

object FullNode {

  def forConfig(appConfig: PeerNodeConfig)(
      implicit F: ConcurrentEffect[IO],
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[FullNode] = {
    val config                   = appConfig.core
    implicit val chainId: BigInt = config.genesis.chainId
    for {
      _ <- if (config.logHandler.contains("file")) {
        ScribeLog.setRootHandlers[IO](
          ScribeLog.consoleHandler(Some(Level.fromName(config.logLevel))),
          ScribeLogPlatform.fileHandler(config.logDir, Some(Level.fromName(config.logLevel)))
        )
      } else {
        ScribeLog.setRootHandlers[IO](
          ScribeLog.consoleHandler(Some(Level.fromName(config.logLevel)))
        )
      }
      metrics  <- Metrics.default[IO]
      keystore <- KeyStorePlatform[IO](config.keystoreDir)
      minerKey <- config.mining.minerKeyPair match {
        case None if config.mining.enabled =>
          keystore.unlockAccount(config.mining.minerAddress, "").map(_.keyPair.some)
        case None     => IO.pure(None)
        case Some(kp) => IO.pure(kp.some)
      }
      history <- History
        .forBackendAndPath[IO](config.history.dbBackend, config.chainDataDir)(F, chainId, T, metrics)
      blockPool <- BlockPool(history, BlockPoolConfig())
      clique    <- Clique(config.mining, config.genesis, history, minerKey)
      consensus = new CliqueConsensus[IO](clique, blockPool)
      peerManager <- PeerManagerPlatform[IO](config, history)
      executor    <- BlockExecutor[IO](config.history, consensus, peerManager)
      syncManager <- SyncManager(config.sync, executor)(F, T, metrics)
      miner       <- BlockMiner[IO](config.mining, syncManager)

      // mount rpc
      publicAPI = PublicApiImpl(config.history, miner)
      personalAPI <- PersonalApiImpl(keystore, history, config.history, executor.txPool)
      adminAPI = AdminApiImpl(peerManager)
      rpc      = RpcService().mountAPI(publicAPI).mountAPI(personalAPI).mountAPI(adminAPI)
      server   = WsServer.bind(config.rpc.addr, rpc.pipe, metrics, Some(rpc.handle _))
      haltWhenTrue <- SignallingRef[IO, Boolean](true)
    } yield FullNode(appConfig, syncManager, miner, keystore, rpc, server, haltWhenTrue)
  }

  def resource(config: PeerNodeConfig)(
      implicit F: ConcurrentEffect[IO],
      T: Timer[IO],
      CS: ContextShift[IO]
  ): Resource[IO, FullNode] =
    FileUtil.lock(Paths.get(s"${config.core.lockPath}")).flatMap[FullNode] { _ =>
      Resource.liftF(forConfig(config))
    }

  def stream(config: PeerNodeConfig)(
      implicit F: ConcurrentEffect[IO],
      T: Timer[IO],
      CS: ContextShift[IO]
  ): Stream[IO, FullNode] = Stream.resource(resource(config))
}
