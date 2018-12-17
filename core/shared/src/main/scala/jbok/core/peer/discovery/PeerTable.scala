package jbok.core.peer.discovery

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.core.peer.discovery.PeerTable._
import jbok.core.peer.{PeerNode, PeerStore}
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.util.Random

case class PeerTable[F[_]](
    selfNode: PeerNode,
    store: PeerStore[F],
    bootstrapNodes: Vector[PeerNode],
    buckets: Ref[F, Vector[Bucket]]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {

  private[this] val log = jbok.common.log.getLogger("PeerTable")

  def getBuckets: F[Vector[Bucket]] =
    buckets.get

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

  /** bump up or add or back up the node */
  def addNode(node: PeerNode): F[Unit] =
    if (node.id == selfNode.id) {
      F.unit
    } else {
      for {
        bucket <- getBucket(node.id)
        updated = bucket.bumpOrAdd(node)
        _ <- putBucket(node.id, updated)
      } yield ()
    }

  def delNode(node: PeerNode): F[Unit] =
    for {
      bucket <- getBucket(node.id)
      updated = bucket.deleteEntry(node)
      _ <- putBucket(node.id, updated)
    } yield ()

  def loadSeedNodes: F[Unit] =
    for {
      seeds <- store.getSeeds(seedCount, seedMaxAge).map(_.toVector)
      full = seeds ++ bootstrapNodes
      _ <- full.traverse(node => {
        log.info(s"found seed ${node} in database")
        addNode(node)
      })
    } yield ()

  def closest(target: ByteVector, maxSize: Int = bucketSize): F[NodesByDistance] = {
    val result = NodesByDistance(Vector.empty, target)
    for {
      buckets <- getBuckets
      entries = buckets.flatMap(_.entries)
    } yield {
      entries.foldLeft(result)((acc, cur) => acc.pushed(cur, maxSize))
    }
  }

//  /**
//    * adds the given node to the table. Compared to plain [[addNode]]
//    * there is an additional safety measure: if the table is still
//    * initializing the node is not added. This prevents an attack where the
//    * table could be filled by just sending ping repeatedly.
//    */
//  def addThroughPing(node: PeerNode): F[Unit] =
//    initDone.get.ifM(addNode(node), F.unit)

  def stream: Stream[F, Unit] =
    Stream.awakeEvery[F](persistInterval).evalMap(_ => persistentNodes)

  /**
    * adds nodes from the table to the database if they have been in the table
    * longer then minTableTime.
    */
  private def persistentNodes = {
    val now = System.currentTimeMillis()
    for {
      buckets <- buckets.get
    } yield ()
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

  /** a [[Bucket]] is just a list of [[PeerNode]]s and a list of backup [[PeerNode]]s */
  case class Bucket(entries: Vector[PeerNode], replacements: Vector[PeerNode]) {
    def bump(n: PeerNode): Bucket =
      if (entries.contains(n)) {
        this.copy(entries = Vector(n) ++ entries.filterNot(_.id == n.id))
      } else {
        this
      }

    def bumpOrAdd(node: PeerNode): Bucket =
      if (entries.contains(node)) {
        bump(node)
      } else if (entries.length < bucketSize) {
        addEntry(node).delReplacement(node)
      } else {
        addReplacement(node)
      }

    private def addReplacement(node: PeerNode): Bucket =
      copy(replacements = Vector(node) ++ replacements.take(maxReplacements - 1))

    private def delReplacement(node: PeerNode): Bucket =
      copy(replacements = replacements.filterNot(_.id == node.id))

    private def addEntry(node: PeerNode): Bucket =
      copy(entries = Vector(node) ++ entries.take(bucketSize - 1))

    private[jbok] def deleteEntry(node: PeerNode): Bucket =
      copy(entries = entries.filterNot(_.id == node.id))

    /**
      * replace removes n from the replacement list and replaces 'last' with it if it is the
      * last entry in the bucket. If 'last' isn't the last entry, it has either been replaced
      * with someone else or became active.
      */
    def replace(last: PeerNode): Bucket =
      if (entries.isEmpty || entries.last.id != last.id) {
        this
      } else {
        if (replacements.isEmpty) {
          deleteEntry(last)
        } else {
          val r = Random.shuffle(replacements).head
          delReplacement(r).copy(entries = entries.updated(entries.length - 1, r))
        }
      }

    override def toString: String =
      s"Bucket(entries: [${entries.mkString(",")}], replacements: [${replacements.mkString(",")}])"
  }

  object Bucket {
    val empty: Bucket = Bucket(Vector.empty, Vector.empty)
  }

  def logDist(a: ByteVector, b: ByteVector): Int =
    a.bits.xor(b.bits).toIndexedSeq.indexWhere(_ == true) match {
      case -1 => a.length.toInt * 8
      case x  => a.length.toInt * 8 - x
    }

  def ordByTargetDist(target: ByteVector): Ordering[ByteVector] =
    Ordering.by((v: ByteVector) => v.xor(target).toIterable)

  case class NodesByDistance(entries: Vector[PeerNode], target: ByteVector) {
    val ord = ordByTargetDist(target)
    def pushed(node: PeerNode, maxSize: Int = bucketSize): NodesByDistance = {
      val updated = entries.indexWhere(x => ord.compare(node.id, x.id) <= 0) match {
        case -1 if entries.length < maxSize => entries ++ Vector(node)
        case -1                             => entries
        case i                              => entries.slice(0, i) ++ Vector(node) ++ entries.slice(i, entries.length)
      }
      copy(entries = updated)
    }
  }

  def apply[F[_]: ConcurrentEffect](
      selfNode: PeerNode,
      store: PeerStore[F],
      bootstrapNodes: Vector[PeerNode]
  )(implicit T: Timer[F]): F[PeerTable[F]] =
    for {
      buckets  <- Ref.of[F, Vector[Bucket]](Vector.fill(nBuckets)(Bucket.empty))
    } yield PeerTable[F](selfNode, store, bootstrapNodes, buckets)
}
