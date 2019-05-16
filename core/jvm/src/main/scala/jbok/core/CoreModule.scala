package jbok.core

import java.nio.channels.AsynchronousChannelGroup

import better.files.File
import cats.effect._
import cats.effect.concurrent.{Ref, Semaphore}
import distage.{Producer => _, _}
import jbok.common.metrics.Metrics
import jbok.common.thread.ThreadUtil
import jbok.core.config.Configs._
import jbok.core.consensus.Consensus
import jbok.core.consensus.poa.clique.{Clique, CliqueConsensus}
import jbok.core.keystore.{KeyStore, KeyStorePlatform}
import jbok.core.ledger.{BlockExecutor, History, HistoryImpl}
import jbok.core.messages.SignedTransactions
import jbok.core.mining.BlockMiner
import jbok.core.models.Block
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.core.peer._
import jbok.core.pool.{BlockPool, OmmerPool, TxPool}
import jbok.core.queue.{Consumer, Producer, Queue}
import jbok.core.queue.memory.MemoryQueue
import jbok.core.sync.{HttpSyncService, SyncClient, SyncService, SyncStatus}
import jbok.core.validators.TxValidator
import jbok.network.Message
import jbok.network.http.HttpClients
import jbok.persistent.KeyValueDB
import org.http4s.client.Client

class CoreModule[F[_]: TagK](implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) extends ModuleDef {
  implicit lazy val acg: AsynchronousChannelGroup = ThreadUtil.acgGlobal

  addImplicit[Bracket[F, Throwable]]
  addImplicit[ContextShift[F]]
  addImplicit[Timer[F]]
  addImplicit[Sync[F]]
  addImplicit[Concurrent[F]]
  addImplicit[ConcurrentEffect[F]]
  addImplicit[AsynchronousChannelGroup]
  make[BigInt].from((config: CoreConfig) => config.genesis.chainId)

  make[HistoryConfig].from((config: CoreConfig) => config.history)
  make[TxPoolConfig].from((config: CoreConfig) => config.txPool)
  make[BlockPoolConfig].from((config: CoreConfig) => config.blockPool)
  make[OmmerPoolConfig].from((config: CoreConfig) => config.ommerPool)
  make[MiningConfig].from((config: CoreConfig) => config.mining)
  make[SyncConfig].from((config: CoreConfig) => config.sync)
  make[PeerConfig].from((config: CoreConfig) => config.peer)

  make[Metrics[F]].fromEffect(Metrics.default[F])
  make[KeyValueDB[F]].fromEffect(KeyValueDB.inmem[F])
  make[History[F]].from[HistoryImpl[F]]
  make[KeyStore[F]].fromEffect(KeyStorePlatform[F](File.newTemporaryDirectory().pathAsString))
  make[Clique[F]].fromEffect((config: CoreConfig, history: History[F]) => Clique[F](config.mining, config.genesis, history, config.mining.minerKeyPair))
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

  make[IncomingManager[F]]
  make[OutgoingManager[F]]
  make[PeerStore[F]].fromEffect((db: KeyValueDB[F]) => PeerStore[F](db))
  make[PeerManager[F]]
  make[PeerMessageHandler[F]]

  // executor & miner
  make[BlockPool[F]]
  make[TxPool[F]]
  make[OmmerPool[F]]

  make[Ref[F, SyncStatus]].fromEffect(Ref.of[F, SyncStatus](SyncStatus.Booting))

  make[TxValidator[F]]
  make[Semaphore[F]].fromEffect(Semaphore[F](1))
  make[BlockExecutor[F]]
  make[BlockMiner[F]]

  // sync
  make[SyncService[F]]
  make[HttpSyncService[F]]
  make[Client[F]].fromResource(HttpClients.okHttp[F])
  make[SyncClient[F]]

  make[CoreNode[F]]
}

object CoreModule {
  def configModule(config: CoreConfig): ModuleDef = new ModuleDef {
    make[CoreConfig].from(config)
  }

  def locator[F[_]: TagK](implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Injector().produceF[F](new CoreModule[F]()).toCats

  def withConfig[F[_]: TagK](config: CoreConfig)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Injector().produceF[F](new CoreModule[F]().overridenBy(configModule(config))).toCats
}
