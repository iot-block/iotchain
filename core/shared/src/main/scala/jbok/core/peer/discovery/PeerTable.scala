package jbok.core.peer.discovery

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.core.peer.{PeerNode, PeerStore}
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.util.Random

sealed abstract class ErrDiscovery(reason: String) extends Exception(reason)

case object ErrPacketTooSmall   extends ErrDiscovery("too small")
case object ErrBadHash          extends ErrDiscovery("bad hash")
case object ErrExpired          extends ErrDiscovery("expired")
case object ErrUnsolicitedReply extends ErrDiscovery("unsolicited reply")
case object ErrUnknownNode      extends ErrDiscovery("unknown node")
case object ErrTimeout          extends ErrDiscovery("RPC timeout")
case object ErrClockWarp        extends ErrDiscovery("reply deadline too far in the future")
case object ErrClosed           extends ErrDiscovery("socket closed")

import jbok.core.peer.discovery.PeerTable._

case class Bucket(
    entries: Vector[PeerNode],
    replacements: Vector[PeerNode]
) {
  def bumped(n: PeerNode): Bucket =
    if (entries.contains(n)) {
      this.copy(entries = Vector(n) ++ entries.filterNot(_.id == n.id))
    } else {
      this
    }
}

object Bucket {
  def empty: Bucket = Bucket(Vector.empty, Vector.empty)
}

case class PeerTable[F[_]](
    selfNode: PeerNode,
    store: PeerStore[F],
    bootstrapNodes: Vector[PeerNode],
    buckets: Ref[F, Vector[Bucket]],
    initDone: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {

  private[this] val log = org.log4s.getLogger

  import PeerTable._

  // TODO
  def getFails(id: ByteVector): F[Int] = F.pure(0)

  // TODO
  def putFails(id: ByteVector, n: Int): F[Unit] = F.unit

  // TODO
  def querySeeds(maxCount: Int, maxAge: FiniteDuration): F[Vector[PeerNode]] = F.pure(Vector.empty)

  /**
    * bucket returns the bucket for the given node ID hash.
    */
  def getBucket(id: ByteVector): F[Bucket] = {
    val d = logDist(selfNode.id, id)
    val i = if (d <= bucketMinDistance) 0 else d - bucketMinDistance - 1
    buckets.get.map(_(i))
  }

  def putBucket(id: ByteVector, bucket: Bucket): F[Unit] = {
    val d = logDist(selfNode.id, id)
    val i = if (d <= bucketMinDistance) 0 else d - bucketMinDistance - 1
    buckets.update(_.updated(i, bucket))
  }

  /**
    * add attempts to add the given node to its corresponding bucket.
    * If the bucket has space available, adding the node succeeds immediately.
    * Otherwise, the node is added if the least recently active node in the bucket does not respond to a ping packet.
    */
  def add(n: PeerNode): F[Unit] =
    if (n.id == selfNode.id) {
      F.unit
    } else {
      bumpOrAdd(n) *> addReplacement(n)
    }

  /**
    * adds the given node to the table. Compared to plain
    * [[add]] there is an additional safety measure: if the table is still
    * initializing the node is not added. This prevents an attack where the
    * table could be filled by just sending ping repeatedly.
    */
  def addThroughPing(node: PeerNode): F[Unit] =
    F.ifM(initDone.get)(add(node), F.unit)

  /**
    * stuff adds nodes the table to the end of their corresponding bucket
    * if the bucket is not full
    */
  def stuff(nodes: Vector[PeerNode]): F[Unit] =
    nodes.traverse(bumpOrAdd).void

  // delete removes an entry from the node table. It is used to evacuate dead nodes.
  def delete(node: PeerNode): F[Unit] =
    for {
      bucket <- getBucket(node.id)
      newBucket = bucket.copy(entries = deleteNode(bucket.entries, node))
      _ <- putBucket(node.id, newBucket)
    } yield ()

  def addReplacement(node: PeerNode): F[Unit] =
    for {
      bucket <- getBucket(node.id)
      newBucket = bucket.copy(replacements = pushNode(bucket.replacements, node, maxReplacements))
      _ <- putBucket(node.id, newBucket)
    } yield ()

  /**
    * replace removes n from the replacement list and replaces 'last' with it if it is the
    * last entry in the bucket. If 'last' isn't the last entry, it has either been replaced
    * with someone else or became active.
    */
  def replace(last: PeerNode): F[Unit] =
    for {
      bucket <- getBucket(last.id)
      _ <- if (bucket.entries.isEmpty || bucket.entries.last.id != last.id) {
        // Entry has moved, don't replace it.
        F.unit
      } else {
        if (bucket.replacements.isEmpty) {
          delete(last)
        } else {
          val r            = Random.shuffle(bucket.replacements).head
          val replacements = deleteNode(bucket.replacements, r)
          val entries      = bucket.entries.updated(bucket.entries.length - 1, r)
          val updated = bucket.copy(
            entries = entries,
            replacements = replacements
          )
          putBucket(last.id, updated)
        }
      }
    } yield ()

  /**
    * bumpOrAdd moves n to the front of the bucket entry list or adds it if the list isn't
    * full. The return value is true if n is in the bucket.
    */
  def bumpOrAdd(n: PeerNode): F[Unit] =
    for {
      bucket <- getBucket(n.id)
      bumped = bucket.bumped(n)
      _ <- if (bucket == bumped) {
        F.unit
      } else {
        val newEntries      = pushNode(bucket.entries, n, bucketSize)
        val newReplacements = deleteNode(bucket.replacements, n)
        val newBucket       = bucket.copy(entries = newEntries, replacements = newReplacements)
        putBucket(n.id, newBucket)
      }
    } yield ()

  /**
    * pushNode adds n to the front of list, keeping at most max items.
    */
  def pushNode(list: Vector[PeerNode], n: PeerNode, max: Int): Vector[PeerNode] =
    Vector(n) ++ list.take(max - 1)

  /**
    * deleteNode removes n from list.
    */
  def deleteNode(list: Vector[PeerNode], n: PeerNode) =
    list.filterNot(_.id == n.id)

  def logDist(a: ByteVector, b: ByteVector): Int =
    a.bits.xor(b.bits).toIndexedSeq.indexWhere(_ == true) match {
      case -1 => a.length.toInt * 8
      case x  => a.length.toInt * 8 - x
    }

  /**
    * closest returns the n nodes in the table that are closest to the given target
    */
  def closest(target: ByteVector, numResults: Int = bucketSize): F[NodesByDistance] = {
    val result = NodesByDistance(Vector.empty, target)
    for {
      buckets <- buckets.get
      entries = buckets.flatMap(_.entries)
    } yield {
      entries.foldLeft(result)((acc, cur) => acc.pushed(cur))
    }
  }

  def stream: Stream[F, Unit] =
    Stream.awakeEvery[F](persistInterval).evalMap(_ => persistentNodes())

  def loadSeedNodes(): F[Unit] =
    for {
      seeds <- querySeeds(seedCount, seedMaxAge)
      full = seeds ++ bootstrapNodes
      _ <- full.traverse(node => {
        log.info(s"found seed ${node} in database")
        add(node)
      })
    } yield ()

  /**
    * adds nodes from the table to the database if they have been in the table
    * longer then minTableTime.
    */
  def persistentNodes() = {
    val now = System.currentTimeMillis()
    for {
      buckets <- buckets.get
    } yield ()
  }
}

case class NodesByDistance(
    entries: Vector[PeerNode],
    target: ByteVector
) {
  def dist(a: ByteVector, b: ByteVector): Long =
    a.xor(b).toLong(signed = false)

  def pushed(node: PeerNode, maxSize: Int = bucketSize): NodesByDistance = {
    val nodeDist = dist(node.id, target)
    val updated = entries.indexWhere(x => dist(x.id, target) > nodeDist) match {
      case -1 if entries.length < maxSize => entries ++ Vector(node)
      case -1                             => entries
      case i                              => entries.slice(0, i) ++ Vector(node) ++ entries.slice(i + 1, entries.length)
    }

    this.copy(entries = updated)
  }
}

object PeerTable {
  val bucketSize      = 16 // Kademlia bucket size
  val maxReplacements = 10 // Size of per-bucket replacement list

  val hashBits          = 32 * 8
  val nBuckets          = hashBits / 15 // Number of buckets
  val bucketMinDistance = hashBits - nBuckets // Log distance of closest bucket

  // IP address limits.
  val bucketIPLimit = 2
  val bucketSubnet  = 24 // at most 2 addresses from the same /24
  val tableIPLimit  = 10
  val tableSubnet   = 24

  val persistInterval  = 30.seconds
  val seedMinTableTime = 5.minutes
  val seedMaxAge       = (5 * 24).hours
  val seedCount        = 30

  val maxFindNodeFailures = 5 // Nodes exceeding this limit are dropped
  val refreshInterval     = 30.minutes
  val revalidateInterval  = 10.seconds

  def apply[F[_]: ConcurrentEffect](
      selfNode: PeerNode,
      store: PeerStore[F],
      bootstrapNodes: Vector[PeerNode]
  )(implicit T: Timer[F]): F[PeerTable[F]] =
    for {
      buckets  <- Ref.of[F, Vector[Bucket]](Vector.fill(nBuckets)(Bucket.empty))
      initDone <- SignallingRef[F, Boolean](false)
    } yield PeerTable[F](selfNode, store, bootstrapNodes, buckets, initDone)
}
