package jbok.core.queue

trait Queue[F[_], K, V] extends Producer[F, K, V] with Consumer[F, K, V]
