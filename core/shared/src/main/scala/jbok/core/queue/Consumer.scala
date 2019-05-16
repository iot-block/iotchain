package jbok.core.queue

import fs2._

trait Consumer[F[_], K, V] {
  def consume: Stream[F, (K, V)]
}
