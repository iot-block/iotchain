package jbok.app.simulations
import java.net.URI

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Topic
import jbok.app.TestnetBuilder.Topology
import jbok.app.api.{NodeInfo, SimulationAPI, SimulationEvent}
import jbok.app.client.JbokClient
import jbok.app.{FullNode, TestnetBuilder}
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.GenesisConfig
import jbok.core.models.{Account, Address}
import jbok.core.peer.PeerNode
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.util.Random

final case class Node(
    nodeInfo: NodeInfo,
    peerNodeUri: String,
    jbokClient: JbokClient
)

class SimulationImpl(
    topic: Topic[IO, Option[SimulationEvent]],
    id2NodeNetwork: Ref[IO, Map[String, Node]],
    id2Node: Ref[IO, Map[String, Node]]
)(implicit F: ConcurrentEffect[IO], T: Timer[IO], CS: ContextShift[IO])
    extends SimulationAPI {
  import SimulationImpl._
  private[this] val log = jbok.common.log.getLogger("SimulationImpl")

  implicit val chainId: BigInt              = 1
  val genesisConfigChainId: GenesisConfig   = GenesisConfig.generate(chainId, Map.empty)
  val txGraphGen: TxGraphGen                = new TxGraphGen(genesisConfigChainId, nAddr = 10)
  val genesisConfigWithAlloc: GenesisConfig = txGraphGen.genesisConfig
  val blockTime: FiniteDuration             = 5.seconds

  override def createNodesWithMiner(n: Int, m: Int): IO[List[NodeInfo]] = {
    val configs =
      TestnetBuilder()
        .withN(n)
        .withBalance(BigInt("1000000000000000"))
        .withChainId(1)
        .withTopology(Topology.Ring)
        .withMiners(m)
        .build
        .unsafeRunSync()

    val nodeInfos = configs.map(infoFromNode)
    log.info(s"create $n node(s)")

    for {
      miner <- FullNode.forConfig(configs.head)
      _     <- miner.start
      _     <- T.sleep(3.seconds)
      nodes <- configs.tail.traverse(FullNode.forConfig)
      _     <- nodes.traverse(_.start)
      _     <- T.sleep(3.seconds)
      _ = log.info("node start, then client to connect")
      jbokClients <- nodeInfos.traverse[IO, JbokClient](x => jbok.app.client.JbokClient(new URI(x.rpcAddr)))
      miner = jbokClients.head
      _ <- txGraphGen.keyPairs.toList.traverse[IO, Address](kp =>
        miner.personal.importRawKey(kp.keyPair.secret.bytes, ""))
      _ <- id2NodeNetwork.update(
        _ ++ nodeInfos
          .zip(jbokClients)
          .map {
            case (ni, jbokClient) =>
              ni.id -> Node(ni, ni.id, jbokClient)
          }
          .toMap)
      _ <- stxStream(2).compile.drain.start
    } yield nodeInfos
  }

  override def getNodes: IO[List[NodeInfo]] = id2NodeNetwork.get.map(_.values.toList.map(_.nodeInfo))

  override def getNodeInfo(id: String): IO[Option[NodeInfo]] =
    id2NodeNetwork.get.map(_.get(id).map(_.nodeInfo))

  override def stopNode(id: String): IO[Unit] =
    for {
      nodes <- id2NodeNetwork.get
      _     <- nodes.get(id).traverse(_.jbokClient.admin.stop)
    } yield ()

  override def getAccounts: IO[List[(Address, Account)]] = IO { txGraphGen.accountMap.toList }

  override def getCoin(address: Address, value: BigInt): IO[Unit] =
    for {
      nodeIdList <- id2NodeNetwork.get.map(_.keys.toList)
      nodeId = Random.shuffle(nodeIdList).take(1).head
      jbokClientOpt <- id2NodeNetwork.get.map(_.get(nodeId).map(_.jbokClient))
      _ <- jbokClientOpt.traverse[IO, ByteVector](jbokClient =>
        jbokClient.public.sendRawTransaction(txGraphGen.getCoin(address, value).asBytes))
    } yield ()

  override def addNode(interface: String, port: Int): IO[Option[String]] =
    for {
      jbokClient <- jbok.app.client.JbokClient(new URI(s"ws://$interface:$port"))
      peerNodeUriOpt <- jbokClient.admin.peerNodeUri.timeout(requestTimeout).attempt.map {
        case Left(_)            => None
        case Right(peerNodeUri) => peerNodeUri.some
      }
      _ <- peerNodeUriOpt.traverse[IO, Unit] { peerNodeUri =>
        id2Node.update { id2NodeMap =>
          id2NodeMap + (peerNodeUri -> Node(NodeInfo(peerNodeUri, interface, port), peerNodeUri, jbokClient))
        }
      }
    } yield peerNodeUriOpt

  override def deleteNode(peerNodeUri: String): IO[Unit] =
    for {
      _ <- id2Node.update(_ - peerNodeUri)
    } yield ()

  override def submitStxsToNetwork(nStx: Int): IO[Unit] =
    for {
      nodeIdList <- id2NodeNetwork.get.map(_.keys.toList)
      nodeId = Random.shuffle(nodeIdList).take(1).head
      _ <- submitStxsToNode(nStx, nodeId)
    } yield ()

  override def submitStxsToNode(nStx: Int, id: String): IO[Unit] =
    for {
      jbokClientOpt <- id2NodeNetwork.get.map(_.get(id).map(_.jbokClient))
      _    = log.trace(s"submit ${nStx} txs in network")
      stxs = txGraphGen.nextValidTxs(nStx)
      _ <- jbokClientOpt
        .map(jbokClient => stxs.traverse[IO, ByteVector](stx => jbokClient.public.sendRawTransaction(stx.asBytes)))
        .getOrElse(IO.unit)
    } yield ()

  private def stxStream(nStx: Int): Stream[IO, Unit] =
    Stream
      .awakeEvery[IO](blockTime)
      .evalMap[IO, Unit] { _ =>
        submitStxsToNetwork(nStx)
      }
      .handleErrorWith[IO, Unit] { err =>
        Stream.eval(IO.delay(log.error(err)))
      }
      .onFinalize[IO](IO.delay(log.info("stx stream stop.")))

  private def infoFromNode(config: FullNodeConfig): NodeInfo = {
    val uri = PeerNode((config.peer.nodekey match {
      case Some(keyPair) => keyPair
      case None          => ???
    }).public, config.peer.host, config.peer.port, config.peer.discoveryPort).uri.toString
    NodeInfo(uri, config.peer.host, config.rpc.port)
  }
}

object SimulationImpl {
  val requestTimeout: FiniteDuration = 5.seconds

  def apply()(implicit F: ConcurrentEffect[IO], T: Timer[IO], CS: ContextShift[IO]): IO[SimulationImpl] =
    for {
      topic          <- Topic[IO, Option[SimulationEvent]](None)
      id2NodeNetwork <- Ref.of[IO, Map[String, Node]](Map.empty)
      id2Node        <- Ref.of[IO, Map[String, Node]](Map.empty)
    } yield new SimulationImpl(topic, id2NodeNetwork, id2Node)
}
