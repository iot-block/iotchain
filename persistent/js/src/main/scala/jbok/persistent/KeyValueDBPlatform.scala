package jbok.persistent
import cats.effect.Sync

trait KeyValueDBPlatform {
  def _forBackendAndPath[F[_]: Sync](backend: String, path: String): F[KeyValueDB[F]] =
    backend match {
      case "inmem" => KeyValueDB.inmem[F]
      case x       => throw new Exception(s"backend ${x} is not supported")
    }
}
