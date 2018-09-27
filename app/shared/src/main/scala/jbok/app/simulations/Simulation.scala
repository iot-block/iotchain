package jbok.app.simulations
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.Executors

import cats.effect.{ConcurrentEffect, IO, Timer}
import cats.implicits._
import fs2.async.Ref
import fs2.async.mutable.Topic
import fs2.{Pipe, _}
import jbok.app.api.{FilterManager, PrivateAPI, PublicAPI}
import jbok.app.simulations.Simulation.NodeId
import jbok.core.Configs.{FilterConfig, FullNodeConfig}
import jbok.core.consensus.Consensus
import jbok.core.consensus.poa.clique.{Clique, CliqueConfig, CliqueConsensus}
import jbok.core.genesis.GenesisConfig
import jbok.core.keystore.KeyStore
import jbok.core.ledger.BlockExecutor
import jbok.core.messages.Message
import jbok.core.mining.BlockMiner
import jbok.core.models.{Address, Block, SignedTransaction}
import jbok.core.peer.PeerManager
import jbok.core.pool.{BlockPool, OmmerPool, TxPool}
import jbok.core.sync.{Broadcaster, SyncService, Synchronizer}
import jbok.core.{FullNode, History}
import jbok.crypto.signature.KeyPair
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.network.rpc.RpcServer
import jbok.network.rpc.RpcServer._
import jbok.network.server.{Server, TcpServerBuilder}
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

import scala.collection.mutable.{ListBuffer => MList}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

class Simulation(val topic: Topic[IO, Option[SimulationEvent]],
                 val nodes: Ref[IO, Map[NodeId, FullNode[IO]]],
                 val miners: Ref[IO, Map[NodeId, FullNode[IO]]])
    extends SimulationAPI {
  private[this] val log = org.log4s.getLogger

  val cliqueConfig = CliqueConfig(period = 5.seconds)
  val txGraphGen   = new TxGraphGen(10)

  private def newAPIServer[API](api: API, enable: Boolean, address: String, port: Int): IO[Option[Server[IO, String]]] =
    if (enable) {
      for {
        rpcServer <- RpcServer()
        _    = rpcServer.mountAPI[API](api)
        _    = log.info("api rpc server binding...")
        bind = new InetSocketAddress(address, port)
        _    = log.info("api rpc server bind done")
        server <- Server(TcpServerBuilder[IO, String], bind, rpcServer.pipe)
      } yield Some(server)
    } else { IO(None) }

  private def newFullNode(config: FullNodeConfig,
                          history: History[IO],
                          consensus: Consensus[IO],
                          blockPool: BlockPool[IO])(implicit F: ConcurrentEffect[IO],
                                                    EC: ExecutionContext,
                                                    T: Timer[IO]): IO[FullNode[IO]] = {
    val managerPipe: Pipe[IO, Message, Message] = _.flatMap(m => Stream.empty.covary[IO])
    for {
      peerManager <- PeerManager[IO](config.peer, history, managerPipe)
      executor = BlockExecutor[IO](config.blockChainConfig, history, blockPool, consensus)
      txPool    <- TxPool[IO](peerManager)
      ommerPool <- OmmerPool[IO](history)
      broadcaster = new Broadcaster[IO](peerManager)
      synchronizer <- Synchronizer[IO](peerManager, executor, txPool, ommerPool, broadcaster)
      syncService  <- SyncService[IO](peerManager, history)
      random = new SecureRandom()
      keyStore      <- KeyStore[IO](config.keystore.keystoreDir, random)
      miner         <- BlockMiner[IO](synchronizer)
      filterManager <- FilterManager[IO](miner, keyStore, new FilterConfig())
      publicAPI <- PublicAPI(history,
                             config.blockChainConfig,
                             config.miningConfig,
                             miner,
                             keyStore,
                             filterManager,
                             config.rpcApi.publicApiVersion)
      publicApiServer <- newAPIServer[PublicAPI](publicAPI,
                                                 config.rpcApi.publicApiEnable,
                                                 config.rpcApi.publicApiBindAddress.toString,
                                                 config.rpcApi.publicApiBindAddress.port.get)
      privateAPI <- PrivateAPI(keyStore, history, config.blockChainConfig, txPool)
      privateApiServer <- newAPIServer[PrivateAPI](privateAPI,
                                                   config.rpcApi.privateApiEnable,
                                                   config.rpcApi.privateApiBindAddress.toString,
                                                   config.rpcApi.privateApiBindAddress.port.get)
    } yield
      new FullNode[IO](config,
                       peerManager,
                       synchronizer,
                       syncService,
                       keyStore,
                       miner,
                       publicApiServer,
                       privateApiServer)
  }

  override def createNodesWithMiner(n: Int, m: Int): IO[List[NodeInfo]] = {
    log.info("in createNodes")
    val fullNodeConfigs = FullNodeConfig.fill(n)
    val signers = (1 to n)
      .map(_ => {
        SecP256k1.generateKeyPair().unsafeRunSync()
      })
      .toList
    val (configs, minerSingers) = selectMiner(n, m, fullNodeConfigs, signers)

    log.info(minerSingers.toString)
    val genesisConfig =
      GenesisConfig.default
        .copy(alloc = txGraphGen.alloc,
              extraData = Clique.fillExtraData(minerSingers.map(Address(_))),
              timestamp = System.currentTimeMillis())
    log.info(s"create ${n} node(s)")

    for {
      newNodes <- configs.zipWithIndex.parTraverse { ci =>
        implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
        IO.shift(ec) *> {
          val sign = (bv: ByteVector) => { SecP256k1.sign(bv.toArray, signers(ci._2)) }
          for {
            db      <- KeyValueDB.inMemory[IO]
            history <- History[IO](db)
            _       <- history.loadGenesisConfig(genesisConfig)
            clique = Clique[IO](cliqueConfig, history, Address(signers(ci._2)), sign)
            blockPool <- BlockPool(history)
            consensus = new CliqueConsensus[IO](blockPool, clique)
          } yield newFullNode(ci._1, history, consensus, blockPool).unsafeRunSync()
        }
      }
      _ <- nodes.modify(_ ++ newNodes.map(x => x.id                                                   -> x).toMap)
      _ <- miners.modify(_ ++ newNodes.filter(n => n.config.miningConfig.miningEnabled).map(x => x.id -> x).toMap)
    } yield newNodes.map(x => NodeInfo[IO](x))
  }

  override def createNodes(n: Int): IO[List[NodeInfo]] = ???

  override def deleteNode(id: String): IO[Unit] =
    for {
      node <- getNode(id)
      _    <- node.stop
      _    <- nodes.modify(_ - id)
      _    <- miners.modify(_ - id)
    } yield ()

  override def startNetwork: IO[Unit] = {
    log.info(s"network start all nodes")
    for {
      xs <- nodes.get
      _  <- xs.values.toList.map(x => startNode(x.id)).sequence
    } yield ()
  }

  override def stopNetwork: IO[Unit] = {
    log.info(s"network stop all nodes")
    for {
      xs <- nodes.get
      _  <- xs.values.toList.map(x => stopNode(x.id)).sequence
    } yield ()
  }

  private def getNode(id: NodeId): IO[FullNode[IO]] = nodes.get.map(xs => xs(id))

  override def getNodes: IO[List[NodeInfo]] = nodes.get.map(_.values.toList.map(n => NodeInfo[IO](n)))

  override def getMiners: IO[List[NodeInfo]] = miners.get.map(_.values.toList.map(n => NodeInfo[IO](n)))

  override def stopMiners(ids: List[String]): IO[Unit] = {
    val r = ids.map(id => miners.get.map(_(id).miner.stop.unsafeRunSync()).unsafeRunSync())
    ids.map(id => miners.modify(_ - id).unsafeRunSync())
    IO.pure(Unit)
  }

  private def createConfigs(n: Int, m: Int): List[FullNodeConfig] = {
    val fullNodeConfigs = FullNodeConfig.fill(n)
    if (m == 0) fullNodeConfigs
    else {
      val gap = n / m
      fullNodeConfigs.zipWithIndex.map {
        case (config, index) =>
          if (index % gap == 0) config.copy(miningConfig = config.miningConfig.copy(miningEnabled = true))
          else config
      }
    }
  }

  private def selectMiner(n: Int,
                          m: Int,
                          fullNodeConfigs: List[FullNodeConfig],
                          signers: List[KeyPair]): (List[FullNodeConfig], List[KeyPair]) =
    if (m == 0) (fullNodeConfigs, List.empty)
    else {
      val gap                    = (n + m - 1) / m
      val miners: MList[KeyPair] = MList.empty
      val configs = fullNodeConfigs.zip(signers).zipWithIndex.map {
        case ((config, signer), index) =>
          if (index % gap == 0) {
            miners += signer
            config.copy(miningConfig = config.miningConfig.copy(miningEnabled = true))
          } else config
      }
      (configs, miners.toList)
    }

  override def setMiner(ids: List[String]): IO[Unit] = {
    val newMiners = ids.map(id => nodes.get.map(_(id)).unsafeRunSync())
    newMiners.map(_.miner.start.unsafeRunSync())
    miners.modify(_ ++ newMiners.map(x => x.id -> x).toMap)
    IO.pure(Unit)
  }

  override def getNodeInfo(id: String): IO[NodeInfo] = getNode(id).map(x => NodeInfo[IO](x))

  override def startNode(id: String): IO[Unit] =
    for {
      node <- getNode(id)
      _    <- node.start
    } yield NodeInfo(node)

  override def stopNode(id: String): IO[Unit] =
    for {
      node <- getNode(id)
      _    <- node.stop
    } yield NodeInfo(node)

  override def connect(topology: String): IO[Unit] = topology match {
    case "ring" =>
      val xs = nodes.get.unsafeRunSync().values.toList
      IO {
        (xs :+ xs.head).sliding(2).foreach {
          case a :: b :: Nil =>
            a.peerManager.connect(b.peerBindAddress).unsafeRunSync()
          case _ =>
            ()
        }
      }
    case "star" =>
      val xs = nodes.get.unsafeRunSync().values.toList
      IO { xs.tail.foreach(node => node.peerManager.connect(xs.head.peerBindAddress).unsafeRunSync()) }
    case _ => IO.raiseError(new RuntimeException(s"${topology} not supportted"))
  }

  override def events: fs2.Stream[IO, SimulationEvent] = ???

  override def submitStxsToNetwork(nStx: Int, t: String): IO[Unit] =
    for {
      nodeIdList <- nodes.get.map(_.keys.toList)
      nodeId = Random.shuffle(nodeIdList).take(1).head
      _ <- submitStxsToNode(nStx, t, nodeId)
    } yield ()

  override def submitStxsToNode(nStx: Int, t: String, id: String): IO[Unit] = {
    val minerTxPool = nodes.get.unsafeRunSync()(id).synchronizer.txPool
    val stxs = t match {
      case "DoubleSpend" => txGraphGen.nextDoubleSpendTxs2(nStx)
      case _             => txGraphGen.nextValidTxs(nStx)
    }
    minerTxPool.addTransactions(stxs)
  }

  override def getBestBlock: IO[List[Block]] =
    nodes.get.map(_.values.toList.map(_.synchronizer.history.getBestBlock.unsafeRunSync()))

  override def getPendingTx: IO[List[List[SignedTransaction]]] =
    nodes.get.map(_.values.toList.map(_.synchronizer.txPool.getPendingTransactions.unsafeRunSync().map(_.stx)))

  override def getShakedPeerID: IO[List[List[String]]] =
    for {
      nodesMap <- nodes.get
      peerIds = nodesMap.values.toList
        .map(_.peerManager.handshakedPeers.unsafeRunSync().values.toList.map(_.peerId.value))
    } yield peerIds

  override def getBlocksByNumber(number: BigInt): IO[List[Block]] =
    nodes.get.map(_.values.toList.map(_.synchronizer.history.getBlockByNumber(number).unsafeRunSync().get))
}

object Simulation {
  type NodeId = String
  def apply()(implicit ec: ExecutionContext): IO[Simulation] =
    for {
      topic  <- fs2.async.topic[IO, Option[SimulationEvent]](None)
      nodes  <- fs2.async.refOf[IO, Map[NodeId, FullNode[IO]]](Map.empty)
      miners <- fs2.async.refOf[IO, Map[NodeId, FullNode[IO]]](Map.empty)
    } yield new Simulation(topic, nodes, miners)
}
