package jbok.core.queue

import fs2.Pipe

trait Producer[F[_], K, V] {
  def produce(key: K, value: V): F[Unit]

  def sink: Pipe[F, (K, V), Unit]
}
