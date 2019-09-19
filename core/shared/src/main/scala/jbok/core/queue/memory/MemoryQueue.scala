package jbok.core.queue.memory

import cats.effect.Concurrent
import cats.implicits._
import fs2.Pipe
import fs2.concurrent.{Queue => Fs2Queue}
import jbok.core.queue.Queue

final class MemoryQueue[F[_], K, V](queue: Fs2Queue[F, (K, V)]) extends Queue[F, K, V] {
  override def produce(key: K, value: V): F[Unit] =
    queue.enqueue1(key -> value)

  override def sink: Pipe[F, (K, V), Unit] =
    queue.enqueue

  override def consume: fs2.Stream[F, (K, V)] =
    queue.dequeue
}

object MemoryQueue {
  def apply[F[_], K, V](implicit F: Concurrent[F]): F[Queue[F, K, V]] =
    Fs2Queue.circularBuffer[F, (K, V)](1000000).map(queue => new MemoryQueue(queue))
}
