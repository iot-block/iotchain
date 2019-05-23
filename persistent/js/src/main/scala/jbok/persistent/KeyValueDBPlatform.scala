package jbok.persistent

import cats.effect.{Resource, Sync}

object KeyValueDBPlatform {
  def resource[F[_]](config: PersistConfig)(implicit F: Sync[F]): Resource[F, KeyValueDB[F]] =
    config.driver match {
      case "inmem" => Resource.liftF(KeyValueDB.inmem[F])
      case driver  => Resource.liftF(F.raiseError(new IllegalArgumentException(s"database driver=${driver} is not supported")))
    }
}
