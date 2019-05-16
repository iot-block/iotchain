package jbok.app

import java.nio.channels.FileLock
import java.nio.file.Paths

import cats.effect._
import fs2._
import jbok.common.FileUtil
import jbok.common.log.Logger
import jbok.core.CoreNode

final class FullNode[F[_]](
    core: CoreNode[F],
    httpRpcService: HttpRpcService[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  def lock: Resource[F, FileLock] =
    FileUtil[F].lock(Paths.get(s"${core.config.lockPath}"))

  def stream: Stream[F, Unit] =
    Stream.resource(lock).flatMap { _ =>
      Stream.eval_(log.i(s"Node start identity=${core.config.identity}")) ++
        Stream(
          core.stream,
          httpRpcService.stream
        ).parJoinUnbounded
          .handleErrorWith(e => Stream.eval(log.w("FullNode error", e)))
          .onFinalize(log.i(s"identity=${core.config.identity} Node ready to exit, bye bye..."))
    }
}

//object FullNode {
//
//  def forConfig(appConfig: PeerNodeConfig)(
//      implicit F: ConcurrentEffect[IO],
//      T: Timer[IO],
//      CS: ContextShift[IO]
//  ): IO[FullNode] = {
//    val config                   = appConfig.core
//    implicit val chainId: BigInt = config.genesis.chainId
//    for {
//      _ <- if (config.logHandler.contains("file")) {
//        Log.setRootHandlers(
//          Log.consoleHandler(Some(Level.fromName(config.logLevel))),
//          LogJVM.fileHandler(config.logDir, Some(Level.fromName(config.logLevel)))
//        )
//      } else {
//        Log.setRootHandlers(
//          Log.consoleHandler(Some(Level.fromName(config.logLevel)))
//        )
//      }
//      metrics  <- Metrics.default[IO]
//      keystore <- KeyStorePlatform[IO](config.keystoreDir)
//      minerKey <- config.mining.minerKeyPair match {
//        case None if config.mining.enabled =>
//          keystore.unlockAccount(config.mining.minerAddress, "").map(_.keyPair.some)
//        case None     => IO.pure(None)
//        case Some(kp) => IO.pure(kp.some)
//      }
//      history <- History
//        .forBackendAndPath[IO](config.history.dbBackend, config.chainDataDir)(F, chainId, T, metrics)
//      blockPool <- BlockPool(history, BlockPoolConfig())
//      clique    <- Clique(config.mining, config.genesis, history, minerKey)
//      consensus = new CliqueConsensus[IO](clique, blockPool)
//      peerManager <- PeerManagerPlatform[IO](config, history)
//      executor    <- BlockExecutor[IO](config.history, consensus, peerManager)
//      syncManager <- SyncManager(config.sync, executor)(F, T, metrics)
//      miner       <- BlockMiner[IO](config.mining, syncManager)
