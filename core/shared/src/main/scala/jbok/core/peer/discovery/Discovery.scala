package jbok.core.peer.discovery

import java.net.InetSocketAddress
import java.util.UUID

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, Resource, Timer}
import cats.implicits._
import fs2._
import jbok.core.config.Configs.PeerConfig
import jbok.core.peer.discovery.KadPacket._
import jbok.core.peer.discovery.PeerTable._
import jbok.core.peer.{PeerNode, PeerStore}
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.network.transport.UdpTransport
import scodec.bits.ByteVector
import jbok.common._

import scala.concurrent.duration._
import scala.util.Random

sealed abstract class ErrDiscovery(reason: String) extends Exception(reason)

case object ErrBadHash          extends ErrDiscovery("bad hash")
case object ErrExpired          extends ErrDiscovery("expired")
case object ErrUnsolicitedReply extends ErrDiscovery("unsolicited reply")
case object ErrUnknownNode      extends ErrDiscovery("unknown node")
case object ErrTimeout          extends ErrDiscovery("transport timeout")
case object ErrClockWarp        extends ErrDiscovery("reply deadline too far in the future")
case object ErrClosed           extends ErrDiscovery("socket closed")

final class Discovery[F[_]](
    config: PeerConfig,
    transport: UdpTransport[F],
    val keyPair: KeyPair,
    val table: PeerTable[F],
    promises: Ref[F, Map[UUID, Deferred[F, KadPacket]]],
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  import Discovery._

  private[this] val log = org.log4s.getLogger("Discovery")

  val store = table.store

  val peerNode = table.selfNode

  private def sendAndWaitPacket(remote: InetSocketAddress, packet: KadPacket): F[KadPacket] =
    Resource
      .make {
        Deferred[F, KadPacket].flatMap(p => promises.update(_ + (packet.id -> p)).as(packet.id -> p))
      } {
        case (id, _) =>
          promises.update(_ - id)
      }
      .use {
        case (_, promise) =>
          transport.send[KadPacket](remote, packet) >> promise.get.timeoutTo(timeout, F.raiseError(ErrTimeout))
      }

  /** send a [[Ping]] message to the remote node and wait for the [[Pong]]. */
  def ping(remote: PeerNode): F[Pong] =
    for {
      current <- T.clock.realTime(MILLISECONDS)
      id      <- F.delay(UUID.randomUUID())
      ping = Ping(peerNode, current + ttl.toMillis, id)
      kad <- sendAndWaitPacket(remote.udpAddress, ping)
      pong = kad.asInstanceOf[Pong]
    } yield pong

  /** re send a [[Ping]] message to the remote node when received a [[Ping]] from it */
  def reping(remote: PeerNode, id: UUID): F[Unit] =
    for {
      current <- T.clock.realTime(MILLISECONDS)
      ping = Ping(peerNode, current + ttl.toMillis, id)
      _ <- sendAndWaitPacket(remote.udpAddress, ping).start
    } yield ()

  /** send a [[FindNode]] request to the remote node and wait for the [[Neighbours]] */
  def findNode(remote: PeerNode, targetPK: KeyPair.Public): F[Vector[PeerNode]] = {
    def go(remote: PeerNode, targetPK: KeyPair.Public): F[Neighbours] =
      for {
        current  <- T.clock.realTime(MILLISECONDS)
        received <- store.getLastPing(remote.id)
        elapsed = current - received
        _ <- if (elapsed > bondExpiration.toMillis) {
          log.debug(s"solicit a ping to ${remote.udpAddress} and wait peer to ping")
          ping(remote) >> T.sleep(timeout)
        } else {
          F.unit
        }
        id <- F.delay(UUID.randomUUID())
        findNode = FindNode(peerNode, targetPK, current + ttl.toMillis, id)
        neighbours <- sendAndWaitPacket(remote.udpAddress, findNode)
      } yield neighbours.asInstanceOf[Neighbours]

    for {
      fails <- store.getFails(remote.id)
      neighbours <- go(remote, targetPK).attempt.flatMap {
        case Left(e) =>
          for {
            _ <- store.putFails(remote.id, fails + 1)
            _ = log.warn(e)(s"findNode ${remote.id} failed, ${fails + 1} time(s)")
            _ <- if (fails + 1 >= maxFindNodeFailures) {
              log.warn(s"too many findNode failures, delete ${remote.id}")
              table.delNode(remote)
            } else {
              F.unit
            }
          } yield Vector.empty[PeerNode]

        case Right(neighbours) =>
          val nodes = neighbours.nodes.map(x => PeerNode.fromTcpAddr(x.pk, x.udpAddress))
          log.debug(s"found ${nodes.length} node(s)")
          nodes.traverse(table.addNode).as(nodes.toVector)
      }
    } yield neighbours
  }

  /** lookup for a random target to keep the table buckets full */
  def refresh: F[Unit] =
    for {
      _   <- table.loadSeedNodes
      key <- F.liftIO(Signature[ECDSA].generateKeyPair())
      _   <- lookup(key.public)
    } yield ()

  /** ping the last node of a random bucket, bump it up if responded,replace or delete it otherwise */
  def revalidate: F[Unit] = {
    def go(last: PeerNode, i: Int): F[Unit] =
      for {
        bucket <- table.getBucket(last.id)
        updated <- ping(last).attempt.map {
          case Left(_) =>
            bucket.replace(last)
          case Right(_) =>
            bucket.bump(last)
        }
        _ <- table.putBucket(last.id, updated)
      } yield ()

    for {
      buckets <- table.getBuckets
      randomLast = Random
        .shuffle(buckets.zipWithIndex)
        .filter(_._1.entries.nonEmpty)
        .map(t => t._1.entries.last -> t._2)
        .headOption
      _ <- randomLast match {
        case Some((last, i)) => go(last, i)
        case None            => F.unit
      }
    } yield ()
  }

  /**
    * recursive lookup starts by picking [[alpha]] closest known nodes
    * then re-sends [[FindNode]] to nodes it has learned about from previous queries.
    *
    * The lookup terminates when the initiator has queried
    * and gotten responses from the k closest nodes it has seen.
    */
  def lookup(targetPK: KeyPair.Public): F[Vector[PeerNode]] = {
    def go(ask: Vector[PeerNode], seen: Set[ByteVector], result: NodesByDistance): F[NodesByDistance] =
      if (ask.isEmpty) {
        F.pure(result)
      } else {
        findNode(ask.head, targetPK).flatMap { nodes =>
          val unSeen  = nodes.filter(n => !seen.contains(n.id))
          val updated = unSeen.foldLeft(result)((acc, node) => acc.pushed(node))
          go(ask.tail, seen ++ unSeen.map(_.id), updated)
        }
      }

    for {
      init <- table.closest(targetPK.bytes.kec256) // generate initial result set
      ask  = init.entries
      seen = Set(table.selfNode.id)
      result <- go(ask, seen, init)
      _      <- result.entries.traverse(table.addNode)
    } yield result.entries
  }

  private def checkExpiration(now: Long, expiration: Long): F[Unit] =
    if (now > expiration) {
      F.raiseError(ErrExpired)
    } else {
      F.unit
    }

  private val pipe: Pipe[F, (InetSocketAddress, KadPacket), (InetSocketAddress, KadPacket)] = { input =>
    val output = input
      .evalMap[F, Option[(InetSocketAddress, KadPacket)]] {
        case (remote, Ping(from, expiration, id)) =>
          for {
            current <- T.clock.realTime(MILLISECONDS)
            _       <- checkExpiration(current, expiration)
            _ = log.debug(s"receive a ping from ${remote}")
            _ <- store.putLastPing(from.id, current)
            pong = Pong(peerNode, current + ttl.toMillis, id)
            lastPong <- store.getLastPong(from.id)
            _ <- (promises.get.map(_.get(id).isEmpty) && (current - lastPong > bondExpiration.toMillis).pure[F]).ifM(
              F.delay(log.debug(s"re-ping to ${from.udpAddress}"))
                >> reping(from, id),
              F.unit
            )
          } yield Some(remote -> pong)

        case (remote, pong @ Pong(from, expiration, id)) =>
          for {
            current <- T.clock.realTime(MILLISECONDS)
            _       <- checkExpiration(current, expiration)
            _       <- F.delay(log.debug(s"receive a pong from ${remote}"))
            _       <- store.putLastPong(from.id, current)
            _ <- promises.get.map(_.get(id)).flatMap {
              case Some(p) => p.complete(pong)
              case None    => F.raiseError[Unit](ErrUnsolicitedReply)
            }
          } yield None

        case (remote, FindNode(from, pk, expiration, id)) =>
          for {
            current  <- T.clock.realTime(MILLISECONDS)
            _        <- checkExpiration(current, expiration)
            lastPong <- store.getLastPong(from.id)
            _ <- if (current - lastPong > bondExpiration.toMillis) {
              F.raiseError(ErrUnknownNode)
            } else {
              F.unit
            }
            _      = log.debug(s"received FindNode request from ${remote}")
            target = pk.bytes.kec256
            xs <- table.closest(target, PeerTable.bucketSize).map(_.entries)
            neighbors = Neighbours(peerNode, xs.toList, current + ttl.toMillis, id)
          } yield Some(remote -> neighbors)

        case (remote, n @ Neighbours(_, _, expiration, id)) =>
          for {
            current <- T.clock.realTime(MILLISECONDS)
            _       <- checkExpiration(current, expiration)
            _ = log.debug(s"received Neighbours response from ${remote}")
            _ <- promises.get.map(_.get(id)).flatMap {
              case Some(p) => p.complete(n)
              case None    => F.raiseError[Unit](ErrUnsolicitedReply)
            }
          } yield None
      }

    output.unNone
  }

  val serve: Stream[F, Unit] =
    transport.serve(pipe)

  val stream: Stream[F, Unit] =
    Stream(
      serve,
      Stream.awakeEvery[F](refreshInterval).evalMap(_ => refresh),
      Stream.awakeEvery[F](revalidateInterval).evalMap(_ => revalidate)
    ).parJoinUnbounded
}

object Discovery {
  val alpha               = 3 // Kademlia concurrency factor
  val timeout             = 5.seconds
  val ttl                 = 20.seconds
  val bondExpiration      = 24.hours
  val maxFindNodeFailures = 5

  def apply[F[_]: ConcurrentEffect](
      config: PeerConfig,
      transport: UdpTransport[F],
      keyPair: KeyPair,
      store: PeerStore[F]
  )(implicit T: Timer[F]): F[Discovery[F]] =
    for {
      promises <- Ref.of[F, Map[UUID, Deferred[F, KadPacket]]](Map.empty)
      selfNode = PeerNode(keyPair.public, config.host, config.port, config.discoveryPort)
      table <- PeerTable[F](selfNode, store, Vector.empty)
    } yield new Discovery[F](config, transport, keyPair, table, promises)
}
