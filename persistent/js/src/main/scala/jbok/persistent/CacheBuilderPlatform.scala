package jbok.persistent

import cats.effect.Sync
import scalacache.Cache

trait CacheBuilderPlatform extends CacheBuilder {
  override def build[F[_], A](maximumSize: Int)(implicit F: Sync[F]): F[Cache[A]] =
    F.raiseError(new Exception("not supported"))
}

