package jbok.persistent
import cats.effect.Sync

trait KeyValueDBPlatform {
  def _forPath[F[_]: Sync](path: String): F[KeyValueDB[F]] = path match {
    case KeyValueDB.INMEM => KeyValueDB.inmem[F]
    case _                => Sync[F].raiseError(new Exception(s"not supported"))
  }
}
