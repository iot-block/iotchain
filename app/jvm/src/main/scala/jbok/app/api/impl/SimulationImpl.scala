package jbok.app.simulations
import java.util.concurrent.Executors
import java.net.URI

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.Topic
import jbok.app.FullNode
import jbok.app.api.{NodeInfo, SimulationAPI, SimulationEvent}
import jbok.common.execution._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.GenesisConfig
import jbok.core.config.defaults.testReference
import jbok.core.consensus.poa.clique.Clique
import jbok.core.models.{Account, Address}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.sdk.client.{JbokClient => sdkClient}
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.collection.mutable.{ListBuffer => MList}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.Random

case class Node(
    nodeInfo: NodeInfo,
    peerNodeUri: String,
    jbokClient: sdkClient
)

class SimulationImpl(
    topic: Topic[IO, Option[SimulationEvent]],
    id2NodeNetwrok: Ref[IO, Map[String, Node]],
    id2Node: Ref[IO, Map[String, Node]]
) extends SimulationAPI {
  import SimulationImpl._
  private[this] val log = jbok.common.log.getLogger("SimulationImpl")

  implicit val chainId: BigInt              = 1
  val genesisConfigChainId: GenesisConfig   = GenesisConfig.generate(chainId, Map.empty)
  val txGraphGen: TxGraphGen                = new TxGraphGen(genesisConfigChainId, nAddr = 10)
  val genesisConfigWithAlloc: GenesisConfig = txGraphGen.genesisConfig

  override def createNodesWithMiner(n: Int, m: Int): IO[List[NodeInfo]] = {
    val fullNodeConfigs = FullNodeConfig.fill(testReference.withMining(_.copy(period = 10.seconds)), n)
    val signers = (1 to n).toList
      .traverse[IO, KeyPair](_ => Signature[ECDSA].generateKeyPair[IO]())
      .unsafeRunSync()
    val (configs, minersKP) = selectMiner(n, m, fullNodeConfigs, signers)
    val genesisConfig       = Clique.generateGenesisConfig(genesisConfigWithAlloc, minersKP.map(Address(_)))

    log.info(s"create $n node(s)")

    for {
      newNodes <- configs.zipWithIndex.traverse[IO, FullNode[IO]] {
        case (config, idx) =>
          implicit val ec: ExecutionContextExecutor =
            ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2, namedThreadFactory(s"EC$idx", daemon = true)))
          val fullNodeConfig = config.copy(genesisOrPath = Left(genesisConfig))
          FullNode.forConfig(fullNodeConfig)
      }
      _ <- newNodes.tail.traverse[IO, Unit](_.peerManager.addPeerNode(newNodes.head.peerManager.peerNode))
      _ <- newNodes.traverse(_.start)
      _ = T.sleep(5.seconds)
      _ = log.info("node start, then client to connect")
      jbokClients <- newNodes.traverse[IO, sdkClient](x => jbok.app.client.JbokClient(new URI(infoFromNode(x).rpcAddr)))
      _ <- id2NodeNetwrok.update(
        _ ++ newNodes
          .zip(jbokClients)
          .map {
            case (fullNode, jbokClient) =>
              fullNode.peerManager.peerNode.uri.toString -> Node(infoFromNode(fullNode),
                                                                 fullNode.peerManager.peerNode.uri.toString,
                                                                 jbokClient)
          }
          .toMap)
    } yield newNodes.map(x => infoFromNode(x))
  }

  override def getNodes: IO[List[NodeInfo]] = id2NodeNetwrok.get.map(_.values.toList.map(_.nodeInfo))

  override def getNodeInfo(id: String): IO[Option[NodeInfo]] =
    id2NodeNetwrok.get.map(_.get(id).map(_.nodeInfo))

  override def stopNode(id: String): IO[Unit] =
    for {
      nodes <- id2NodeNetwrok.get
      _     <- nodes.get(id).traverse(_.jbokClient.admin.stop)
    } yield ()

  override def getAccounts: IO[List[(Address, Account)]] = IO { txGraphGen.accountMap.toList }

  override def getCoin(address: Address, value: BigInt): IO[Unit] =
    for {
      nodeIdList <- id2NodeNetwrok.get.map(_.keys.toList)
      nodeId = Random.shuffle(nodeIdList).take(1).head
      jbokClientOpt <- id2NodeNetwrok.get.map(_.get(nodeId).map(_.jbokClient))
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

  override def submitStxsToNetwork(nStx: Int, t: String): IO[Unit] =
    for {
      nodeIdList <- id2NodeNetwrok.get.map(_.keys.toList)
      _      = log.info("in submitstx")
      nodeId = Random.shuffle(nodeIdList).take(1).head
      _ <- submitStxsToNode(nStx, t, nodeId)
    } yield ()

  override def submitStxsToNode(nStx: Int, t: String, id: String): IO[Unit] =
    for {
      jbokClientOpt <- id2NodeNetwrok.get.map(_.get(id).map(_.jbokClient))
      stxs = t match {
        case "DoubleSpend" => txGraphGen.nextDoubleSpendTxs2(nStx)
        case _             => txGraphGen.nextValidTxs(nStx)
      }
      _ <- jbokClientOpt
        .map(jbokClient => stxs.traverse[IO, ByteVector](stx => jbokClient.public.sendRawTransaction(stx.asBytes)))
        .getOrElse(IO.unit)
    } yield ()

  private def infoFromNode(fullNode: FullNode[IO]): NodeInfo =
    NodeInfo(fullNode.peerManager.peerNode.uri.toString, fullNode.config.peer.host, fullNode.config.rpc.port)

  private def selectMiner(
      n: Int,
      m: Int,
      fullNodeConfigs: List[FullNodeConfig],
      signers: List[KeyPair]
  ): (List[FullNodeConfig], List[KeyPair]) =
    if (m == 0) (fullNodeConfigs, List.empty)
    else {
      val gap                    = (n + m - 1) / m
      val miners: MList[KeyPair] = MList.empty
      val configs = fullNodeConfigs.zip(signers).zipWithIndex.map {
        case ((config, signer), index) =>
          if (index % gap == 0) {
            miners += signer
            config.withMining(_.copy(enabled = true, minerAddressOrKey = Right(signer)))
          } else {
            config.withMining(_.copy(enabled = false, minerAddressOrKey = Right(signer)))
          }
      }
      (configs, miners.toList)
    }
}

object SimulationImpl {
  val requestTimeout: FiniteDuration = 5.seconds

  def apply()(implicit ec: ExecutionContext): IO[SimulationImpl] =
    for {
      topic          <- Topic[IO, Option[SimulationEvent]](None)
      id2NodeNetwork <- Ref.of[IO, Map[String, Node]](Map.empty)
      id2Node        <- Ref.of[IO, Map[String, Node]](Map.empty)
    } yield new SimulationImpl(topic, id2NodeNetwork, id2Node)
}
