//package jbok.core
//
//import java.security.SecureRandom
//
//import cats.effect.{ConcurrentEffect, Timer}
//import cats.implicits._
//import fs2._
//import fs2.async.mutable.Signal
//import jbok.core.configs.FullNodeConfig
//import jbok.core.keystore.KeyStore
//import jbok.core.ledger.{BlockPool, Ledger, OmmersPool}
//import jbok.core.messages.Status
//import jbok.core.peer.PeerManager
//import jbok.core.sync.{Broadcaster, RegularSync, SyncService}
//import jbok.core.validators.Validators
//import jbok.evm.VM
//import jbok.network.server.Server
//import scodec.bits.ByteVector
//
//import scala.concurrent.ExecutionContext
//
//case class FullNode[F[_]](
//    config: FullNodeConfig,
//    peerManager: PeerManager[F],
//    txPool: TxPool[F],
//    regularSync: RegularSync[F],
//    syncService: SyncService[F],
//    rpcServer: Server[F],
//    stopWhenTrue: Signal[F, Boolean]
//)(implicit F: ConcurrentEffect[F], EC: ExecutionContext, T: Timer[F]) {
//  private[this] val log = org.log4s.getLogger
//
//  val id = config.nodeId
//
//  val rpcBindAddress = config.network.rpcBindAddress
//
//  val peerBindAddress = config.network.peerBindAddress
//
//  def stream =
//    Stream(
//      txPool.stream,
//      regularSync.stream,
//      syncService.stream
//    ).join(8)
//
//  def start: F[Unit] =
//    stopWhenTrue.get.flatMap {
//      case true =>
//        stopWhenTrue.set(false) *>
//          F.start(stream.interruptWhen(stopWhenTrue).compile.drain).void *>
//          F.delay(log.info(s"full node started"))
//      case false => F.unit
//    }
//
//  def serveWhile: Stream[F, Nothing] =
//    Stream.bracket(start)(_ => stopWhenTrue.discrete.takeWhile(_ === false).drain, _ => stop)
//
//  def stop: F[Unit] = stopWhenTrue.get.flatMap {
//    case true  => F.unit
//    case false => stopWhenTrue.set(true) *> F.delay(log.info(s"full node stopped"))
//  }
//}
//
//object FullNode {
//  def inMemory[F[_]](config: FullNodeConfig)(
//      implicit F: ConcurrentEffect[F],
//      EC: ExecutionContext,
//      T: Timer[F]
//  ): F[FullNode[F]] = {
//    val status = Status(1, 1, ByteVector.empty, ByteVector.empty)
//    val random = new SecureRandom()
//    for {
//      blockChain <- BlockChain.inMemory[F]
//      peerManager <- PeerManager[F](config.peer, blockChain)
//      txPoolConfig = TxPoolConfig()
//      txPool <- TxPool[F](peerManager, txPoolConfig)
//      blockPool <- BlockPool[F](blockChain, 10, 10)
//      ommersPool <- OmmersPool[F](blockChain)
//      broadcaster = new Broadcaster[F](peerManager)
//      vm = new VM
//      validators = Validators[F](blockChain)
//      ledger = new Ledger[F](vm, blockChain, config.blockChainConfig, validators, blockPool)
//      regularSync <- RegularSync[F](peerManager, ledger, ommersPool, txPool, broadcaster)
//      syncService <- SyncService[F](peerManager, blockChain)
//      keyStore = KeyStore[F](config.keystore.keystoreDir, random)
////      apiImpl = PrivateAPI[F](keyStore, blockChain, txPool)
////      apiService = JsonRPCService[F].mountAPI[PrivateAPI[F]](apiImpl)
//      rpcServer <- Server[F](config.network.rpcBindAddress, apiService)
//      stopWhenTrue <- fs2.async.signalOf[F, Boolean](true)
//    } yield
//      FullNode[F](
//        config,
//        peerManager,
//        txPool,
//        regularSync,
//        syncService,
//        rpcServer,
//        stopWhenTrue
//      )
//  }
//}
