package jbok.persistent

import cats.data.OptionT
import cats.effect.{Sync, Timer}
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.common.metrics.Metrics
import scodec.bits.ByteVector

object DBErr {
  case object NotFound extends Exception("NotFound")
}

abstract class KeyValueDB[F[_]](implicit F: Sync[F]) {
  protected[jbok] def getRaw(key: ByteVector): F[Option[ByteVector]]

  protected[jbok] def putRaw(key: ByteVector, newVal: ByteVector): F[Unit]

  protected[jbok] def delRaw(key: ByteVector): F[Unit]

  protected[jbok] def hasRaw(key: ByteVector): F[Boolean]

  protected[jbok] def keysRaw: F[List[ByteVector]]

  protected[jbok] def size: F[Int]

  protected[jbok] def toMapRaw: F[Map[ByteVector, ByteVector]]

  protected[jbok] def writeBatchRaw(put: List[(ByteVector, ByteVector)], del: List[ByteVector]): F[Unit]

  def keys[Key: RlpCodec](namespace: ByteVector): F[List[Key]]

  def toMap[Key: RlpCodec, Val: RlpCodec](namespace: ByteVector): F[Map[Key, Val]]

  final def getOpt[Key: RlpCodec, Val: RlpCodec](key: Key, namespace: ByteVector): F[Option[Val]] =
    for {
      rawkey <- encode[Key](key, namespace)
      rawval <- getRaw(rawkey)
      v      <- rawval.fold(none[Val].pure[F])(x => decode[Val](x).map(_.some))
    } yield v

  final def getOptT[Key: RlpCodec, Val: RlpCodec](key: Key, namespace: ByteVector): OptionT[F, Val] =
    OptionT(getOpt[Key, Val](key, namespace))

  final def get[Key: RlpCodec, Val: RlpCodec](key: Key, namespace: ByteVector): F[Val] =
    getOpt[Key, Val](key, namespace).flatMap(opt => F.fromOption(opt, DBErr.NotFound))

  final def put[Key: RlpCodec, Val: RlpCodec](key: Key, newVal: Val, namespace: ByteVector): F[Unit] =
    for {
      rawkey <- encode[Key](key, namespace)
      rawval <- encode[Val](newVal)
      _      <- putRaw(rawkey, rawval)
    } yield ()

  final def del[Key: RlpCodec](key: Key, namespace: ByteVector): F[Unit] =
    for {
      rawK <- encode[Key](key, namespace)
      _    <- delRaw(rawK)
    } yield ()

  final def has[Key: RlpCodec](key: Key, namespace: ByteVector): F[Boolean] =
    encode[Key](key, namespace) >>= hasRaw

  final def writeBatch[Key: RlpCodec, Val: RlpCodec](put: List[(Key, Val)],
                                                     del: List[Key],
                                                     namespace: ByteVector): F[Unit] =
    for {
      p <- put.traverse { case (k, v) => (encode[Key](k, namespace), encode[Val](v)).tupled }
      d <- del.traverse(k => encode[Key](k, namespace))
      _ <- writeBatchRaw(p, d)
    } yield ()

  final def writeBatch[Key: RlpCodec, Val: RlpCodec](ops: List[(Key, Option[Val])], namespace: ByteVector): F[Unit] = {
    val (a, b) = ops.partition(_._2.isDefined)
    val put    = a.collect { case (k, Some(v)) => k -> v }
    val del    = b.map { case (k, _) => k }
    writeBatch[Key, Val](put, del, namespace)
  }

  def encode[A: RlpCodec](a: A, prefix: ByteVector = ByteVector.empty): F[ByteVector] =
    F.delay(prefix ++ RlpCodec[A].encode(a).require.bytes)

  def decode[A: RlpCodec](bytes: ByteVector, prefix: ByteVector = ByteVector.empty): F[A] =
    F.delay(RlpCodec[A].decode(bytes.drop(prefix.length).bits).require.value)
}

object KeyValueDB extends KeyValueDBPlatform {
  val INMEM = "inmem"

  def inmem[F[_]: Sync]: F[KeyValueDB[F]] = InmemKeyValueDB[F]

  def forBackendAndPath[F[_]: Sync](backend: String, path: String)(implicit T: Timer[F],
                                                                   M: Metrics[F]): F[KeyValueDB[F]] =
    _forBackendAndPath[F](backend, path)
}
