package jbok.persistent

import cats.effect.Sync

import scalacache.{Cache, Entry}
import scalacache.caffeine._
import com.github.benmanes.caffeine.cache.Caffeine

trait CacheBuilderPlatform extends CacheBuilder {

  override def build[F[_], A](maximumSize: Int)(implicit F: Sync[F]): F[Cache[A]] = F.delay {
    val underlying = Caffeine
      .newBuilder()
      .maximumSize(maximumSize)
      .build[String, Entry[A]]

    CaffeineCache(underlying)
  }
}
