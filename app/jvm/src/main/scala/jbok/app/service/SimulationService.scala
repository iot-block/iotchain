package jbok.app.service


import java.net.URI

import cats.{Id, Monad}
import cats.implicits._

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import cats.implicits._
import cats.effect.implicits._
import fs2.concurrent.Topic
import jbok.app.JbokClient
import jbok.common.log.Logger
import jbok.core.models.{Account, Address}
import jbok.sdk.api.{SimulationAPI, SimulationEvent}

import scala.concurrent.duration._
//
//final case class Node[F[_]](
////    nodeInfo: NodeInfo,
//    peerUri: String,
//    jbokClient: JbokClient[F]
//)
//
//final class SimulationImpl[F[_]](
//    topic: Topic[F, Option[SimulationEvent]],
//    id2Node: Ref[F, Map[String, Node[F]]]
//)(implicit F: ConcurrentEffect[F], T: Timer[F], cs: ContextShift[F]) extends SimulationAPI[F] {
//  import SimulationImpl._
//
////  override def getNodes: F[List[NodeInfo]] = id2Node.get.map(_.values.toList.map(_.nodeInfo))
//
//  override def getNodeInfo(id: String): F[Option[NodeInfo]] =
//    id2Node.get.map(_.get(id).map(_.nodeInfo))
//
//  override def stopNode(id: String): F[Unit] =
//    for {
//      nodes <- id2Node.get
//      _     <- nodes.get(id).traverse(_.jbokClient.admin.stop)
//    } yield ()
//
//  override def getAccounts: F[List[(Address, Account)]] = F.pure(Nil)
//
//  override def addNode(interface: String, port: Int): F[Option[String]] =
//    for {
//      jbokClient <- jbok.app.client.JbokClient(new URI(s"ws://$interface:$port"))
//      peerNodeUriOpt <- jbokClient.admin.peerUri.timeout(requestTimeout).attempt.map {
//        case Left(_)            => None
//        case Right(peerNodeUri) => peerNodeUri.some
//      }
//      _ <- peerNodeUriOpt.traverse { peerNodeUri =>
//        id2Node.update { id2NodeMap =>
//          id2NodeMap + (peerNodeUri -> Node(NodeInfo(peerNodeUri, interface, port), peerNodeUri, jbokClient))
//        }
//      }
//    } yield peerNodeUriOpt
//
//  override def deleteNode(peerNodeUri: String): F[Unit] =
//    for {
//      _ <- id2Node.update(_ - peerNodeUri)
//    } yield ()
//}
//
//object SimulationImpl {
//  val requestTimeout: FiniteDuration = 5.seconds
//
////  def apply()(implicit F: ConcurrentEffect[IO], T: Timer[IO], CS: ContextShift[IO]): F[SimulationImpl] =
////    for {
////      topic   <- Topic[IO, Option[SimulationEvent]](None)
////      id2Node <- Ref.of[IO, Map[String, Node]](Map.empty)
////    } yield new SimulationImpl(topic, id2Node)
//}

