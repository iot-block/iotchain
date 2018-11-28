package jbok.persistent

import cats.effect.Sync
import scalacache.Cache

trait CacheBuilder {
  def build[F[_], A](maximumSize: Int)(implicit F: Sync[F]): F[Cache[A]]
}

object CacheBuilder extends CacheBuilder with CacheBuilderPlatform
