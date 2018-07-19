package jbok.persistent

import cats.Traverse
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import scodec.Codec
import scodec.bits.ByteVector

import scala.Option

class KeyValueStore[F[_], K, V](namespace: ByteVector, db: KeyValueDB[F])(
    implicit F: Sync[F],
    ck: Codec[K],
    cv: Codec[V]
) {
  def get(key: K): F[V] =
    for {
      k <- encode[K](key)
      rawV <- db.get(namespace ++ k)
      v <- decode[V](rawV)
    } yield v

  def getOpt(key: K): F[Option[V]] =
    (for {
      k <- OptionT.liftF(encode[K](key))
      rawVOpt <- OptionT(db.getOpt(namespace ++ k))
      v <- OptionT.liftF(decode[V](rawVOpt))
    } yield v).value

  def put(key: K, newVal: V): F[Unit] =
    for {
      k <- encode[K](key)
      newV <- encode[V](newVal)
      _ <- db.put(namespace ++ k, newV)
    } yield ()

  def del(key: K): F[Unit] =
    for {
      k <- encode[K](key)
      _ <- db.del(namespace ++ k)
    } yield ()

  def has(key: K): F[Boolean] =
    for {
      k <- encode[K](key)
      b <- db.has(namespace ++ k)
    } yield b

  def writeBatch[G[_]: Traverse](ops: G[(K, Option[V])]): F[Unit] =
    for {
      encodedOps <- ops.map {
        case (k, v) =>
          for {
            ek <- encode(k)
            ev <- OptionT.fromOption[F](v).flatMap(x => OptionT.liftF(encode(x))).value
          } yield ek -> ev
      }.sequence
      _ <- db.writeBatch(encodedOps)
    } yield ()

  def keys: F[List[K]] = db.keys.flatMap(_.traverse(decode[K]))

  def toMap: F[Map[K, V]] =
    db.toMap
      .flatMap(_.toList.map {
        case (k, v) =>
          decode[K](k).flatMap(dk => decode[V](v).map(dv => dk -> dv))
      }.sequence)
      .map(_.toMap)

  def decode[A](x: ByteVector)(implicit codec: Codec[A]): F[A] = F.delay(codec.decode(x.bits).require.value)

  def encode[A](x: A)(implicit codec: Codec[A]): F[ByteVector] = F.delay(codec.encode(x).require.bytes)
}
