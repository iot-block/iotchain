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
import jbok.crypto.ssl.{SSLConfig, SSLContextHelper}
import jbok.network.Message
import jbok.persistent.{KVStore, KVStorePlatform}

class CoreModule[F[_]: TagK](config: FullConfig)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) extends ModuleDef {
  implicit lazy val acg: AsynchronousChannelGroup = ThreadUtil.acgGlobal

  addImplicit[Bracket[F, Throwable]]
  addImplicit[ContextShift[F]]
  addImplicit[Timer[F]]
  addImplicit[Sync[F]]
  addImplicit[Concurrent[F]]
  addImplicit[ConcurrentEffect[F]]
  addImplicit[AsynchronousChannelGroup]
  make[FullConfig].from(config)
  make[BigInt].from((config: FullConfig) => config.genesis.chainId)

  LoggerPlatform.initConfig[IO](config.log).unsafeRunSync()

  make[HistoryConfig].from((config: FullConfig) => config.history)
  make[TxPoolConfig].from((config: FullConfig) => config.txPool)
  make[BlockPoolConfig].from((config: FullConfig) => config.blockPool)
  make[MiningConfig].from((config: FullConfig) => config.mining)
  make[SyncConfig].from((config: FullConfig) => config.sync)
  make[PeerConfig].from((config: FullConfig) => config.peer)
  make[DatabaseConfig].from((config: FullConfig) => config.db)
  make[ServiceConfig].from((config: FullConfig) => config.service)
  make[KeyStoreConfig].from((config: FullConfig) => config.keystore)
  make[SSLConfig].from((config: FullConfig) => config.ssl)

  make[KVStore[F]].fromResource(KVStorePlatform.resource[F](config.persist))
  make[Metrics[F]].from(new PrometheusMetrics[F]())
  make[History[F]].from[HistoryImpl[F]]
  make[KeyStore[F]].fromEffect((config: KeyStoreConfig) => KeyStorePlatform.withInitKey[F](config))
  make[Clique[F]].fromEffect((config: FullConfig, store: KVStore[F], history: History[F], keystore: KeyStore[F]) => Clique[F](config.mining, config.genesis, store, history, keystore))
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
  make[PeerStore[F]].fromEffect((store: KVStore[F]) => PeerStore[F](store))
  make[PeerManager[F]]
  make[PeerMessageHandler[F]]

  // executor & miner
  make[BlockPool[F]]
  make[TxPool[F]]
  make[Ref[F, NodeStatus]].fromEffect((config: PeerConfig) => Ref.of[F, NodeStatus](NodeStatus.WaitForPeers(0, config.minPeers)))

  make[TxValidator[F]]
  make[Semaphore[F]].fromEffect(Semaphore[F](1))
  make[BlockExecutor[F]]
  make[BlockMiner[F]]
  make[SyncClient[F]]

  make[CoreNode[F]]
}

object CoreModule {
  val testConfig: FullConfig = Config[IO].readResource[FullConfig]("config.test.yaml").unsafeRunSync()
}
