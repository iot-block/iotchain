package jbok.core.peer.discovery
import java.net.InetSocketAddress

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.core.config.Configs.DiscoveryConfig
import jbok.core.peer.{PeerNode, PeerStore}
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.network.transport.UdpTransport
import jbok.persistent.KeyValueDB
import org.bouncycastle.util.BigIntegers
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.util.Random

case class Discovery[F[_]](
    config: DiscoveryConfig,
    keyPair: KeyPair,
    store: PeerStore[F],
    transport: UdpTransport[F],
    table: PeerTable[F],
    promises: Ref[F, Map[ByteVector, Deferred[F, KadPacket]]],
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  import Discovery._

  private[this] val log = org.log4s.getLogger

  val ourEndpoint = Endpoint.makeEndpoint(new InetSocketAddress(config.interface, config.port), config.port)

  /**
    *  sends a [[Ping]] message to the given node and waits for a reply [[Pong]].
    */
  def ping(id: ByteVector, to: InetSocketAddress): F[Pong] = {
    val req = Ping(4, ourEndpoint, Endpoint.makeEndpoint(to, 0), System.currentTimeMillis() + ttl.toMillis)
    for {
      packet  <- encode(req)
      promise <- Deferred[F, KadPacket]
      _       <- promises.update(_ + (packet.mdc -> promise))
      _       <- transport.send(to, packet, Some(timeout))
      kad <- promise.get.timeoutTo(timeout, F.raiseError(ErrTimeout))
      _ <- promises.update(_ - id)
    } yield kad.asInstanceOf[Pong]
  }

  /**
    * sends a [[FindNode]] request to the given node and waits until
    * the node has sent [[Neighbours]].
    */
  def findNode(toId: ByteVector, to: InetSocketAddress, targetPK: KeyPair.Public): F[Neighbours] =
    for {
      elapsed <- store.getLastPingReceived(toId).map(ts => System.currentTimeMillis() - ts)
      _ <- if (elapsed > bondExpiration.toMillis) {
        // If we haven't seen a ping from the destination node for a while, it won't remember
        // our endpoint proof and reject findnode. Solicit a ping first.
        ping(toId, to)
      } else {
        F.unit
      }
      req = FindNode(targetPK, System.currentTimeMillis() + ttl.toMillis)
      p <- Deferred[F, KadPacket]
      _ <- promises.update(_ + (toId -> p))
      _ = log.info(s"findnode from ${toId}")
      packet <- encode(req)
      _      <- transport.send(to, packet, Some(timeout))
      neighbours <- p.get.timeoutTo(timeout, F.raiseError(ErrTimeout))
      _ <- promises.update(_ - toId)
    } yield neighbours.asInstanceOf[Neighbours]

  def findNode(node: PeerNode, targetPK: KeyPair.Public): F[Vector[PeerNode]] =
    for {
      fails <- table.getFails(node.id)
      neighbours <- findNode(node.id, node.addr, targetPK).attempt.flatMap {
        case Left(e) =>
          for {
            _ <- table.putFails(node.id, fails + 1)
            _ = log.warn(e)(s"findNode ${node.id} failed, ${fails + 1} time(s)")
            _ <- if (fails + 1 >= maxFindNodeFailures) {
              log.warn(s"too many findNode failures, delete ${node.id}")
              table.delete(node)
            } else {
              F.unit
            }
          } yield Vector.empty[PeerNode]

        case Right(neighbours) =>
          neighbours.nodes.map(x => PeerNode.fromAddr(x.pk, x.endpoint.addr)).toVector.pure[F]
      }
    } yield neighbours

  /**
    * finds random nodes in the network.
    */
  def lookupRandom(refreshIfEmpty: Boolean): F[Vector[PeerNode]] =
    for {
      key    <- F.liftIO(Signature[ECDSA].generateKeyPair())
      result <- lookup(key.public, refreshIfEmpty)
    } yield result

  def refresh(): F[Unit] =
    for {
      _ <- table.loadSeedNodes()
      _ <- lookupRandom(false)
    } yield ()

  /**
    * checks that the last node in a random bucket is still live
    * and replaces or deletes the node if it isn't.
    */
  def reValidate: F[Unit] = {
    def reValidate0(last: PeerNode, i: Int): F[Unit] =
      for {
        _ <- ping(last.id, last.addr).attempt.map {
          case Left(e) =>
            table.replace(last)
          case Right(_) =>
            for {
              bucket <- table.getBucket(last.id)
              bumped = bucket.bumped(last)
              _ <- table.putBucket(last.id, bumped)
            } yield ()
        }
      } yield ()

    for {
      bs <- table.buckets.get
      randomLast = Random
        .shuffle(bs.zipWithIndex)
        .filter(_._1.entries.nonEmpty)
        .map(t => t._1.entries.last -> t._2)
        .headOption
      _ <- randomLast match {
        case Some((last, i)) => reValidate0(last, i)
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
  def lookup(targetPK: KeyPair.Public, refreshIfEmpty: Boolean): F[Vector[PeerNode]] = {
    def lookup0(ask: Vector[PeerNode], seen: Set[ByteVector], result: NodesByDistance): F[NodesByDistance] =
      if (ask.isEmpty) {
        F.pure(result)
      } else {
        findNode(ask.head, targetPK).flatMap { nodes =>
          val unSeen  = nodes.filter(n => !seen.contains(n.id))
          val updated = unSeen.foldLeft(result)((acc, node) => acc.pushed(node))
          lookup0(ask.tail, seen ++ unSeen.map(_.id), updated)
        }
      }

    for {
      init <- table.closest(targetPK.bytes.kec256) // generate initial result set
      ask  = init.entries
      seen = Set(table.selfNode.id)
      result <- lookup0(ask, seen, init)
    } yield result.entries
  }

  private def checkExpiration(ts: Long): F[Unit] =
    if (System.currentTimeMillis() > ts) {
      F.raiseError(ErrExpired)
    } else {
      F.unit
    }

  private[jbok] def encode(kad: KadPacket): F[UdpPacket] =
    F.delay {
      val payload   = RlpCodec.encode(kad).require.bytes
      val hash      = payload.kec256
      val signature = Signature[ECDSA].sign(hash.toArray, keyPair, None).unsafeRunSync()

      val sigBytes =
        BigIntegers.asUnsignedByteArray(32, signature.r) ++
          BigIntegers.asUnsignedByteArray(32, signature.s) ++
          Array[Byte]((signature.v - 27).toByte)

      val forSha = sigBytes ++ payload.toArray
      val mdc    = forSha.kec256

      UdpPacket(ByteVector(mdc ++ forSha))
    }

  private[jbok] def decode(bytes: ByteVector): F[UdpPacket] =
    if (bytes.length < 98) {
      F.raiseError[UdpPacket](ErrPacketTooSmall)
    } else {
      val packet   = UdpPacket(bytes)
      val mdcCheck = bytes.drop(32).kec256
      if (packet.mdc == mdcCheck) {
        F.pure(packet)
      } else {
        F.raiseError[UdpPacket](ErrBadHash)
      }
    }

  private[jbok] def extract(packet: UdpPacket): F[KadPacket] = F.delay {
    RlpCodec.decode[KadPacket](packet.data.bits).require.value
  }

  val pipe: Pipe[F, (InetSocketAddress, UdpPacket), (InetSocketAddress, UdpPacket)] = { input =>
    val output = input
      .evalMap {
        case (remote, packet) =>
          extract(packet).map(kad => (remote, kad, packet))
      }
      .evalMap[F, List[(InetSocketAddress, KadPacket)]] {
        case (remote, Ping(_, from, to, expiration), packet) =>
          for {
            _ <- checkExpiration(expiration)
            reply = Endpoint.makeEndpoint(remote, from.tcpPort)
            pong  = Pong(reply, packet.mdc, System.currentTimeMillis() + ttl.toMillis)
          } yield List(remote -> pong)

        case (_, pong: Pong, packet) =>
          for {
            _ <- checkExpiration(pong.expiration)
            _ = log.info(s"recived pong from id ${packet.pk}")
            _ <- promises.get.map(_.get(pong.pingHash)).flatMap {
              case Some(p) => p.complete(pong)
              case None    => F.raiseError[Unit](ErrUnsolicitedReply)
            }
          } yield Nil

        case (remote, FindNode(pk, expiration), _) =>
          for {
            _ <- checkExpiration(expiration)
            target = pk.bytes.kec256
            xs <- table.closest(target, PeerTable.bucketSize).map(_.entries)
            neighbors = Neighbours(xs.toList.map(e => Neighbour(Endpoint.makeEndpoint(e.addr, e.port), e.pk)),
                                   System.currentTimeMillis() + ttl.toMillis)
          } yield List(remote -> neighbors)

        case (_, neighbours: Neighbours, packet) =>
          for {
            _ <- checkExpiration(neighbours.expiration)
            _ = log.info(s"neighbor from ${packet.id}")
            _ <- promises.get.map(_.get(packet.id)).flatMap {
              case Some(p) => p.complete(neighbours)
              case None    => F.raiseError[Unit](ErrUnsolicitedReply)
            }
          } yield Nil
      }

    output
      .flatMap(xs => Stream(xs: _*).covary[F])
      .evalMap {
        case (remote, kad) => encode(kad).map(udp => remote -> udp)
      }
  }

  def serve: Stream[F, Unit] =
    transport.serve(pipe)
}

object Discovery {
  val alpha               = 3 // Kademlia concurrency factor
  val timeout             = 5.seconds
  val ttl                 = 20.seconds
  val bondExpiration      = 24.hours
  val maxFindNodeFailures = 5

  def apply[F[_]: ConcurrentEffect](
      config: DiscoveryConfig,
      keyPair: KeyPair,
      transport: UdpTransport[F],
      db: KeyValueDB[F]
  )(implicit T: Timer[F]): F[Discovery[F]] =
    for {
      promises <- Ref.of[F, Map[ByteVector, Deferred[F, KadPacket]]](Map.empty)
      store = new PeerStore[F](db)
      table <- PeerTable[F](PeerNode(keyPair.public, config.interface, config.port), store, Vector.empty)
    } yield Discovery[F](config, keyPair, store, transport, table, promises)
}
