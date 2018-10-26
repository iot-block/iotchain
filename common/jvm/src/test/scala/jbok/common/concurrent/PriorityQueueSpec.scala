package jbok.common.concurrent
import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.JbokSpec
import jbok.common.execution._

class PriorityQueueSpec extends JbokSpec {
  def test[A](input: Vector[(A, Int)]): IO[Vector[A]] =
    for {
      queue <- PriorityQueue.unbounded[IO, Option[A]]
      _ <- Stream
        .emits(input)
        .noneTerminate
        .evalMap[IO, Unit] {
          case Some((e, p)) => queue.enqueue1(e.some, p)
          case None         => queue.enqueue1(None, Int.MinValue)
        }
        .compile
        .drain
      out <- Stream.repeatEval(queue.dequeue1).unNoneTerminate.compile.toVector
    } yield out

  "PriorityQueue" should {
    "dequeue the highest priority elements first" in {
      forAll { elems: Vector[Int] =>
        val input = elems.zipWithIndex
        test(input).unsafeRunSync() shouldBe elems.reverse
      }
    }
  }
}
