package jbok.common.concurrent

// from https://github.com/SystemFw/upperbound/blob/master/src/main/scala/queues.scala
import cats.Order
import cats.collections.Heap
import cats.effect.Concurrent
import cats.effect.concurrent.{Ref, Semaphore}
import fs2._
import cats.implicits._

trait PriorityQueue[F[_], A] {

  /**
    * Enqueues an element. A higher number means higher priority
    */
  def enqueue1(a: A, priority: Int): F[Unit]

  def enqueue: Sink[F, (A, Int)] = _.evalMap(t => enqueue1(t._1, t._2))

  /**
    * Tries to dequeue the higher priority element. In case there
    * are multiple elements with the same priority, they are
    * dequeued in FIFO order
    */
  def dequeue1: F[A]

  def dequeue: Stream[F, A] = Stream.repeatEval(dequeue1)
}

object PriorityQueue {

  /**
    * Unbounded size.
    * `dequeue` immediately fails if the queue is empty
    */
  def naive[F[_], A](implicit F: Concurrent[F]): F[PriorityQueue[F, A]] =
    Ref.of[F, HeapQueue[A]](HeapQueue.empty[A]) map { ref =>
      new PriorityQueue[F, A] {
        def enqueue1(a: A, priority: Int): F[Unit] =
          ref.modify { q =>
            q.enqueue(a, priority) -> q
          }.void

        def dequeue1: F[A] =
          ref.modify { q =>
            q.dequeued -> q.dequeue.map(_.pure[F]).getOrElse(F.raiseError(new NoSuchElementException))
          }.flatten
      }
    }

  /**
    * Unbounded size.
    * `dequeue` blocks on empty queue until an element is available.
    */
  def unbounded[F[_], A](implicit F: Concurrent[F]): F[PriorityQueue[F, A]] =
    Semaphore(0) flatMap { n =>
      naive[F, A] map { queue =>
        new PriorityQueue[F, A] {
          def enqueue1(a: A, priority: Int): F[Unit] =
            queue.enqueue1(a, priority) *> n.release

          def dequeue1: F[A] =
            n.acquire *> queue.dequeue1
        }
      }
    }

  /**
    * Bounded size.
    * `dequeue` blocks on empty queue until an element is available.
    * `enqueue` immediately fails if the queue is full.
    */
  def bounded[F[_], A](maxSize: Int)(implicit F: Concurrent[F]): F[PriorityQueue[F, A]] =
    Semaphore(maxSize.toLong).flatMap { permits =>
      unbounded[F, A].map { queue =>
        new PriorityQueue[F, A] {
          override def enqueue1(a: A, priority: Int): F[Unit] =
            permits.tryAcquire.ifM(
              ifTrue = queue.enqueue1(a, priority),
              ifFalse = F.raiseError(new Exception("LimitReached"))
            )

          override def dequeue1: F[A] =
            queue.dequeue1 <* permits.release
        }
      }
    }

  case class Rank[A](a: A, priority: Int, insertionOrder: Long = 0)

  implicit def rankOrder[A]: Order[Rank[A]] =
    Order.whenEqual(
      Order.reverse(Order.by(_.priority)),
      Order.by(_.insertionOrder)
    )

  case class HeapQueue[A](queue: Heap[Rank[A]], nextId: Long) {
    def enqueue(a: A, priority: Int): HeapQueue[A] = HeapQueue(
      queue add Rank(a, priority, nextId),
      nextId + 1
    )

    def dequeue: Option[A] = queue.getMin.map(_.a)

    def dequeued: HeapQueue[A] = copy(queue = this.queue.remove)
  }

  object HeapQueue {
    def empty[A] = HeapQueue(Heap.empty[Rank[A]], 0)
  }
}
