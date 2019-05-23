package jbok.core

import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.effect.concurrent.{Ref, Semaphore}
import distage.{Producer => _, _}
import javax.net.ssl.SSLContext
import jbok.common.config.Config
import jbok.common.log.LoggerPlatform
import jbok.common.metrics.{Metrics, PrometheusMetrics}
import jbok.common.thread.ThreadUtil
import jbok.core.config._
import jbok.core.consensus.Consensus
import jbok.core.consensus.poa.clique.{Clique, CliqueConsensus}
import jbok.core.keystore.{KeyStore, KeyStorePlatform}
import jbok.core.ledger.{BlockExecutor, History, HistoryImpl}
import jbok.core.messages.SignedTransactions
import jbok.core.mining.BlockMiner
import jbok.core.models.Block
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.core.peer._
import jbok.core.pool.{BlockPool, TxPool}
import jbok.core.queue.memory.MemoryQueue
import jbok.core.queue.{Consumer, Producer, Queue}
import jbok.core.sync.SyncClient
import jbok.core.validators.TxValidator
import jbok.crypto.ssl.SSLContextHelper
import jbok.network.Message
import jbok.persistent.{KeyValueDB, KeyValueDBPlatform}

class CoreModule[F[_]: TagK](config: CoreConfig)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) extends ModuleDef {
  implicit lazy val acg: AsynchronousChannelGroup = ThreadUtil.acgGlobal

  addImplicit[Bracket[F, Throwable]]
  addImplicit[ContextShift[F]]
  addImplicit[Timer[F]]
  addImplicit[Sync[F]]
  addImplicit[Concurrent[F]]
  addImplicit[ConcurrentEffect[F]]
  addImplicit[AsynchronousChannelGroup]
  make[CoreConfig].from(config)
  make[BigInt].from((config: CoreConfig) => config.genesis.chainId)

  LoggerPlatform.initConfig[IO](config.log).unsafeRunSync()

  make[HistoryConfig].from((config: CoreConfig) => config.history)
  make[TxPoolConfig].from((config: CoreConfig) => config.txPool)
  make[BlockPoolConfig].from((config: CoreConfig) => config.blockPool)
  make[MiningConfig].from((config: CoreConfig) => config.mining)
  make[SyncConfig].from((config: CoreConfig) => config.sync)
  make[PeerConfig].from((config: CoreConfig) => config.peer)
  make[DatabaseConfig].from((config: CoreConfig) => config.db)
  make[ServiceConfig].from((config: CoreConfig) => config.service)
  make[KeyStoreConfig].from((config: CoreConfig) => config.keystore)

  make[KeyValueDB[F]].fromResource(KeyValueDBPlatform.resource[F](config.persist))
  make[Metrics[F]].from(new PrometheusMetrics[F]())
  make[History[F]].from[HistoryImpl[F]]
  make[KeyStore[F]].fromResource((config: KeyStoreConfig) => KeyStorePlatform.resource[F](config))
  make[Clique[F]].fromEffect((config: CoreConfig, db: KeyValueDB[F], history: History[F]) => Clique[F](config.mining, db, config.genesis, history))
  make[Consensus[F]].from[CliqueConsensus[F]]

  // peer
  make[Queue[F, Peer[F], SignedTransactions]].fromEffect(MemoryQueue[F, Peer[F], SignedTransactions])
  make[Queue[F, PeerSelector[F], SignedTransactions]].fromEffect(MemoryQueue[F, PeerSelector[F], SignedTransactions])
  make[Queue[F, Peer[F], Block]].fromEffect(MemoryQueue[F, Peer[F], Block])
  make[Queue[F, PeerSelector[F], Block]].fromEffect(MemoryQueue[F, PeerSelector[F], Block])
  make[Queue[F, Peer[F], Message[F]]].fromEffect(MemoryQueue[F, Peer[F], Message[F]])

  make[Consumer[F, Peer[F], SignedTransactions]].from((queue: Queue[F, Peer[F], SignedTransactions]) => queue)
  make[Producer[F, Peer[F], SignedTransactions]].from((queue: Queue[F, Peer[F], SignedTransactions]) => queue)

  make[Consumer[F, PeerSelector[F], SignedTransactions]].from((queue: Queue[F, PeerSelector[F], SignedTransactions]) => queue)
  make[Producer[F, PeerSelector[F], SignedTransactions]].from((queue: Queue[F, PeerSelector[F], SignedTransactions]) => queue)

  make[Consumer[F, Peer[F], Block]].from((queue: Queue[F, Peer[F], Block]) => queue)
  make[Producer[F, Peer[F], Block]].from((queue: Queue[F, Peer[F], Block]) => queue)

  make[Consumer[F, PeerSelector[F], Block]].from((queue: Queue[F, PeerSelector[F], Block]) => queue)
  make[Producer[F, PeerSelector[F], Block]].from((queue: Queue[F, PeerSelector[F], Block]) => queue)

  make[Option[SSLContext]].fromEffect(SSLContextHelper[F](config.ssl))
  make[IncomingManager[F]]
  make[OutgoingManager[F]]
  make[PeerStore[F]].fromEffect((db: KeyValueDB[F]) => PeerStore[F](db))
  make[PeerManager[F]]
  make[PeerMessageHandler[F]]

  // executor & miner
  make[BlockPool[F]]
  make[TxPool[F]]
  make[Ref[F, NodeStatus]].fromEffect(Ref.of[F, NodeStatus](NodeStatus.WaitForPeers))

  make[TxValidator[F]]
  make[Semaphore[F]].fromEffect(Semaphore[F](1))
  make[BlockExecutor[F]]
  make[BlockMiner[F]]
  make[SyncClient[F]]

  make[CoreNode[F]]
}

object CoreModule {
  val testConfig: CoreConfig = Config[IO].readResource[CoreConfig]("config.test.yaml").unsafeRunSync()

  def resource[F[_]: TagK](config: CoreConfig = testConfig)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Injector().produceF[F](new CoreModule[F](config)).toCats
}
