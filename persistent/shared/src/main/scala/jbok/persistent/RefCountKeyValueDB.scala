package jbok.persistent
import cats.Traverse
import cats.data.OptionT
import cats.effect.Sync
import jbok.codec.rlp.RlpCodec
import scodec.bits.ByteVector
import cats.implicits._
import jbok.codec.rlp.codecs._

case class RefCntValue(value: ByteVector, refCnt: Int = 0, lastUsedVersion: BigInt = BigInt(0)) {
  def incRefCnt(amount: Int, version: BigInt): RefCntValue =
    copy(refCnt = refCnt + amount, lastUsedVersion = version)

  def decRefCnt(amount: Int, version: BigInt): RefCntValue =
    copy(refCnt = refCnt - amount, lastUsedVersion = version)
}

case class Snapshot(key: ByteVector, storedNode: Option[RefCntValue])

class RefCountKeyValueDB[F[_]](db: KeyValueDB[F], version: BigInt)(implicit F: Sync[F]) extends KeyValueDB[F] {
  private[this] val log = org.log4s.getLogger

  private val snapshotCountKeyPrefix = ByteVector("sck".getBytes())

  private val snapshotKeyPrefix = ByteVector("sk".getBytes())

  override def get(key: ByteVector): F[ByteVector] =
    getOpt(key).map(_.get)

  override def getOpt(key: ByteVector): F[Option[ByteVector]] =
    db.getOpt(key).map(_.map(bytes => storedNodeFromBytes(bytes).value))

  override def put(key: ByteVector, newVal: ByteVector): F[Unit] =
    writeBatch[List](List(key -> Some(newVal)))

  override def del(key: ByteVector): F[Unit] =
    writeBatch[List](List(key -> None))

  override def has(key: ByteVector): F[Boolean] =
    db.getOpt(key).map(_.isDefined)

  override def keys: F[List[ByteVector]] =
    db.keys

  override def size: F[Int] =
    db.size

  override def toMap: F[Map[ByteVector, ByteVector]] =
    db.toMap

  override def writeBatch[G[_]: Traverse](ops: G[(ByteVector, Option[ByteVector])]): F[Unit] = {
    val (toRemove, toUpsert) =
      ops.foldLeft((List.empty[ByteVector], List.empty[(ByteVector, ByteVector)])) {
        case ((acc1, acc2), (k, Some(v))) =>
          (acc1, acc2 :+ (k -> v))
        case ((acc1, acc2), (k, None)) =>
          (acc1 :+ k, acc2)
      }

    for {
      upsert  <- prepareUpsertChanges(toUpsert, version)
      changes <- prepareRemovalChanges(toRemove, upsert, version)
      (toUpsertUpdated, snapshots) = changes.foldLeft(List.empty[(ByteVector, ByteVector)], List.empty[Snapshot]) {
        case ((upsertAcc, snapshotAcc), (key, (storedNode, theSnapshot))) =>
          (upsertAcc :+ (key -> storedNodeToBytes(storedNode)), snapshotAcc :+ theSnapshot)
      }

      snapshotToSave <- getSnapshotsToSave(version, snapshots)
      _              <- db.writeBatch[List]((toUpsertUpdated ++ snapshotToSave).map(t => t._1 -> Some(t._2)))
    } yield ()
  }

  override def clear(): F[Unit] =
    db.clear()

  /////////////////////////////
  /////////////////////////////

  /**
    * Fetches snapshots stored in the DB for the given version number and deletes the stored nodes, referred to
    * by these snapshots, that meet criteria for deletion (see `getNodesToBeRemovedInPruning` for details).
    *
    * All snapshots for this version are removed, which means state can no longer be rolled back to this point.
    */
  def prune(version: BigInt): F[Unit] =
    getSnapshotCount(version).flatMap {
      case (_, None) => F.unit
      case (snapshotsCountKey, Some(snapshotCount)) =>
        val snapshotKeys: List[ByteVector] = snapshotKeysUpTo(version, snapshotCount)
        val toBeRemoved = getNodesToBeRemovedInPruning(version, snapshotKeys)
        for {
          batch <- toBeRemoved.map(xs => (snapshotsCountKey +: snapshotKeys) ++ xs).map(_.distinct)
          _ <- db.writeBatch[List](batch.map(x => x -> None))
        } yield ()
    }

  def rollback(version: BigInt): F[Unit] =
    getSnapshotCount(version).flatMap {
      case (_, None) => F.unit
      case (_, Some(snapshotCount)) =>
        for {
          snapshots <- snapshotKeysUpTo(version, snapshotCount).traverse(key => db.get(key).map(snapshotFromBytes))
          batch = snapshots.foldLeft(List.empty[(ByteVector, Option[ByteVector])]) {
            case (acc, Snapshot(key, Some(sn))) => acc :+ (key -> Some(storedNodeToBytes(sn)))
            case (acc, Snapshot(key, None))     => acc :+ (key -> None)
          }
          _ <- db.writeBatch(batch)
        } yield ()
    }

  private def getSnapshotCount(version: BigInt): F[(ByteVector, Option[BigInt])] = {
    val snapshotsCountKey = getSnapshotsCountKey(version)
    for {
      snapshotCount <- db.getOpt(snapshotsCountKey).map(_.map(snapshotsCountFromBytes))
    } yield (snapshotsCountKey, snapshotCount)
  }

  private def withSnapshotCount(version: BigInt)(f: (ByteVector, BigInt) => F[Unit]): Unit = {
    val snapshotsCountKey  = getSnapshotsCountKey(version)
    val maybeSnapshotCount = db.getOpt(snapshotsCountKey).map(_.map(snapshotsCountFromBytes))
    maybeSnapshotCount.map {
      case Some(snapshotCount) => f(snapshotsCountKey, snapshotCount)
      case None                => ()
    }
  }

  private def snapshotKeysUpTo(version: BigInt, snapshotCount: BigInt): List[ByteVector] = {
    val getSnapshotKeyFn = getSnapshotKey(version)(_)
    (BigInt(0) until snapshotCount).map(snapshotIndex => getSnapshotKeyFn(snapshotIndex)).toList
  }

  private def getNodesToBeRemovedInPruning(version: BigInt, snapshotKeys: List[ByteVector]): F[List[ByteVector]] =
    snapshotKeys.foldLeftM(List.empty[ByteVector]) { (acc, snapshotKey) =>
      for {
        snapshot <- db.get(snapshotKey).map(snapshotFromBytes)
        node     <- db.get(snapshot.key).map(storedNodeFromBytes)
      } yield {
        if (node.refCnt == 0 && node.lastUsedVersion <= version) {
          acc :+ snapshot.key
        } else {
          acc
        }
      }
    }

  type Changes = Map[ByteVector, (RefCntValue, Snapshot)]
  private def prepareUpsertChanges(toUpsert: List[(ByteVector, ByteVector)], version: BigInt): F[Changes] =
    toUpsert.foldLeftM(Map.empty[ByteVector, (RefCntValue, Snapshot)]) {
      case (acc, (key, value)) =>
        for {
          (storedNode, snapshot) <- getFromChangesOrStorage(key, acc)
            .getOrElse(RefCntValue(value) -> Snapshot(key, None)) // or a new node with ref cnt == 0
        } yield acc + (key -> (storedNode.incRefCnt(1, version), snapshot))
    }

  private def prepareRemovalChanges(toRemove: List[ByteVector], changes: Changes, version: BigInt): F[Changes] =
    toRemove.foldLeftM(changes) {
      case (acc, key) =>
        getFromChangesOrStorage(key, acc).value.map {
          case Some((storedNode, snapshot)) => acc + (key -> (storedNode.decRefCnt(1, version), snapshot))
          case None                         => acc
        }
    }

  /**
    * get snapshots to list of kvs
    */
  private def getSnapshotsToSave(version: BigInt, snapshots: List[Snapshot]): F[List[(ByteVector, ByteVector)]] =
    if (snapshots.isEmpty) {
      F.pure(Nil)
    } else {
      val snapshotCountKey = getSnapshotsCountKey(version)
      val getSnapshotKeyFn = getSnapshotKey(version)(_)
      for {
        versionSnapshotsCount <- db.getOpt(snapshotCountKey).map(_.map(snapshotsCountFromBytes).getOrElse(BigInt(0)))
      } yield {
        val snapshotsToSave = snapshots.zipWithIndex.map {
          case (snapshot, index) =>
            getSnapshotKeyFn(versionSnapshotsCount + index) -> snapshotToBytes(snapshot)
        }
        (snapshotCountKey -> snapshotsCountToBytes(versionSnapshotsCount + snapshotsToSave.size)) +: snapshotsToSave
      }
    }

  private def getFromChangesOrStorage(key: ByteVector, storedNodes: Changes): OptionT[F, (RefCntValue, Snapshot)] =
    OptionT
      .fromOption[F](storedNodes.get(key))
      .orElseF(db.getOpt(key).map(_.map(storedNodeFromBytes).map(sn => sn -> Snapshot(key, Some(sn)))))

  private def storedNodeFromBytes(bytes: ByteVector): RefCntValue =
    RlpCodec.decode[RefCntValue](bytes.bits).require.value

  private def storedNodeToBytes(node: RefCntValue): ByteVector =
    RlpCodec.encode(node).require.bytes

  private def snapshotsCountFromBytes(encoded: ByteVector): BigInt =
    RlpCodec.decode[BigInt](encoded.bits).require.value

  private def snapshotsCountToBytes(bigInt: BigInt): ByteVector =
    RlpCodec.encode(bigInt).require.bytes

  private def snapshotFromBytes(bytes: ByteVector): Snapshot =
    RlpCodec.decode[Snapshot](bytes.bits).require.value

  private def snapshotToBytes(snapshot: Snapshot): ByteVector =
    RlpCodec.encode(snapshot).require.bytes

  /**
    * Key to be used to store BlockNumber -> Snapshots Count
    */
  private def getSnapshotsCountKey(version: BigInt): ByteVector =
    snapshotCountKeyPrefix ++ ByteVector(version.toByteArray)

  /**
    * Returns a snapshot key given a version number and a index
    */
  private def getSnapshotKey(version: BigInt)(index: BigInt): ByteVector =
    snapshotKeyPrefix ++ ByteVector(version.toByteArray) ++ ByteVector(index.toByteArray)
}

object RefCountKeyValueDB {
  def forVersion[F[_]: Sync](db: KeyValueDB[F], version: BigInt) = new RefCountKeyValueDB[F](db, version)
}
