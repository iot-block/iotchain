package jbok.app.simulations
import java.util.concurrent.Executors
import java.net.URI

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.Topic
import jbok.app.FullNode
import jbok.common.execution._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.GenesisConfig
import jbok.core.config.defaults.testReference
import jbok.core.consensus.poa.clique
import jbok.core.models.{Account, Address}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.app.client.JbokClient
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.peer.PeerNode
import scodec.bits.ByteVector
import scala.concurrent.duration._

import scala.collection.mutable.{ListBuffer => MList}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.Random

case class Node(
    nodeInfo: NodeInfo,
    peerNode: PeerNode,
    jbokClient: JbokClient
)

class SimulationImpl(
    topic: Topic[IO, Option[SimulationEvent]],
    id2NodeNetwrok: Ref[IO, Map[String, Node]],
    id2Node: Ref[IO, Map[String, Node]]
) extends SimulationAPI {
  private[this] val log = jbok.common.log.getLogger("SimulationImpl")

  implicit val chainId: BigInt              = 1
  val genesisConfigChainId: GenesisConfig   = GenesisConfig.generate(chainId, Map.empty)
  val txGraphGen: TxGraphGen                = new TxGraphGen(genesisConfigChainId, nAddr = 10)
  val genesisConfigWithAlloc: GenesisConfig = txGraphGen.genesisConfig

  override def createNodesWithMiner(n: Int, m: Int): IO[List[NodeInfo]] = {
    val fullNodeConfigs = FullNodeConfig.fill(testReference, n)
    val signers = (1 to n).toList
      .traverse[IO, KeyPair](_ => Signature[ECDSA].generateKeyPair[IO]())
      .unsafeRunSync()
    val (configs, minersKP) = selectMiner(n, m, fullNodeConfigs, signers)
    val genesisConfig       = clique.generateGenesisConfig(genesisConfigWithAlloc, minersKP.map(Address(_)))

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
      jbokClients <- newNodes.traverse[IO, JbokClient](x => JbokClient(new URI(infoFromNode(x).rpcAddr)))
      _ <- id2NodeNetwrok.update(
        _ ++ newNodes
          .zip(jbokClients)
          .map {
            case (fullNode, jbokClient) =>
              fullNode.config.identity -> Node(infoFromNode(fullNode), fullNode.peerManager.peerNode, jbokClient)
          }
          .toMap)
    } yield newNodes.map(x => infoFromNode(x))
  }

  override def addNode(uri: String): IO[String] =
    // rpc uri
    // get client
    // get peerNode uri
    // ok return peerNode?
    ???

  override def deleteNode(uri: String): IO[Unit] = ???

  override def getNodes: IO[List[NodeInfo]] = id2NodeNetwrok.get.map(_.values.toList.map(_.nodeInfo))

  override def getNodeInfo(id: String): IO[Option[NodeInfo]] = ???

  override def stopNode(id: String): IO[Unit] =
    // rpc adminAPI stop
    ???

//  override def connect(topology: String): IO[Unit] = topology match {
//    case "ring" =>
//      for {
//        jbokClients <- id2Client.get.map(_.toList.sortBy(_._1).map(_._2))
//        _ <- (jbokClients :+ jbokClients.head).sliding(2).toList.traverse[IO, Unit] {
//          case a :: b :: Nil =>
////            a.jbokClient.admin.addPeer(b.peerNode)
//            ???
//          case _ =>
//            IO.unit
//        }
//      } yield ()
//
//    case "star" => ???
//      for {
//        jbokClients <- id2Client.get.map(_.toList.sortBy(_._1).map(_._2))
//        _ = log.info("connect star")
//        statuses <- jbokClients.traverse[IO, Boolean](_.jbokClient.status)
//        _        = log.info(s"${statuses}")
//        peerNode = jbokClients.head.peerNode
//        _ <- jbokClients.tail.traverse[IO, Unit] { client =>
//          log.info("in traverse")
//          log.info(s"${client.jbokClient.admin}")
//          for {
//            _ <- client.jbokClient.admin.addPeer(peerNode)
//            _ = log.info("traverse end.")
//          } yield ()
//        }
//        _ = log.info("connnect start done.")
//      } yield ()
//
//    case _ => IO.raiseError(new RuntimeException(s"${topology} not supported"))
//  }

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
        .map(jbokClient =>
          stxs.traverse[IO, ByteVector](stx =>
            jbokClient.public.sendRawTransaction(RlpCodec.encode(stx).require.bytes)))
        .getOrElse(IO.unit)
    } yield ()

  override def getAccounts(): IO[List[(Address, Account)]] = IO { txGraphGen.accountMap.toList }

  override def getCoin(address: Address, value: BigInt): IO[Unit] =
    for {
      nodeIdList <- id2NodeNetwrok.get.map(_.keys.toList)
      nodeId = Random.shuffle(nodeIdList).take(1).head
      jbokClientOpt <- id2NodeNetwrok.get.map(_.get(nodeId).map(_.jbokClient))
      _ <- jbokClientOpt.traverse[IO, ByteVector](jbokClient =>
        jbokClient.public.sendRawTransaction(RlpCodec.encode(txGraphGen.getCoin(address, value)).require.bytes))
    } yield ()

  private def infoFromNode(fullNode: FullNode[IO]): NodeInfo =
    NodeInfo(fullNode.id, fullNode.config.peer.host, fullNode.config.rpc.port)

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
  type NodeId = String
  def apply()(implicit ec: ExecutionContext): IO[SimulationImpl] =
    for {
      topic          <- Topic[IO, Option[SimulationEvent]](None)
      id2NodeNetwork <- Ref.of[IO, Map[String, Node]](Map.empty)
      id2Node        <- Ref.of[IO, Map[String, Node]](Map.empty)
    } yield new SimulationImpl(topic, id2NodeNetwork, id2Node)
}
