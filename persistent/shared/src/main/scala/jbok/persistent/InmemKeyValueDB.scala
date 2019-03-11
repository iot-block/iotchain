package jbok.persistent

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import scodec.bits.ByteVector

final class InmemKeyValueDB[F[_]: Sync](ref: Ref[F, Map[ByteVector, ByteVector]]) extends KeyValueDB[F] {
  override def getRaw(key: ByteVector): F[Option[ByteVector]] =
    ref.get.map(_.get(key))

  override def putRaw(key: ByteVector, newVal: ByteVector): F[Unit] =
    ref.update(_ + (key -> newVal))

  override def delRaw(key: ByteVector): F[Unit] =
    ref.update(_ - key)

  override def hasRaw(key: ByteVector): F[Boolean] =
    ref.get.map(_.contains(key))

  override def keysRaw: F[List[ByteVector]] =
    ref.get.map(_.keys.toList)

  override def size: F[Int] =
    ref.get.map(_.size)

  override def toMapRaw: F[Map[ByteVector, ByteVector]] =
    ref.get

  override def writeBatchRaw(put: List[(ByteVector, ByteVector)], del: List[ByteVector]): F[Unit] =
    ref.update(xs => xs -- del ++ put)

  override def keys[Key: RlpCodec](namespace: ByteVector): F[List[Key]] =
    keysRaw.map(_.filter(_.startsWith(namespace))).flatMap(_.traverse(k => decode[Key](k, namespace)))

  override def toMap[Key: RlpCodec, Val: RlpCodec](namespace: ByteVector): F[Map[Key, Val]] =
    for {
      mapRaw <- toMapRaw.map(_.filterKeys(_.startsWith(namespace)))
      xs     <- mapRaw.toList.traverse { case (k, v) => (decode[Key](k, namespace), decode[Val](v)).tupled }
    } yield xs.toMap

  override protected[jbok] def close: F[Unit] =
    ref.set(Map.empty)
}

object InmemKeyValueDB {
  def apply[F[_]: Sync]: F[KeyValueDB[F]] =
    Ref.of[F, Map[ByteVector, ByteVector]](Map.empty).map(ref => new InmemKeyValueDB[F](ref))
}
