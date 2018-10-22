package jbok

import fs2._
import cats.effect.IO
import org.scalatest.prop.PropertyChecks
import org.scalatest._
import org.scalatest.concurrent.{AsyncTimeLimitedTests, TimeLimitedTests}
import org.scalatest.time.Span

import scala.concurrent.{Future, TimeoutException}
import scala.concurrent.duration._

trait JbokSpec
    extends WordSpecLike
    with Matchers
    with PropertyChecks
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with TimeLimitedTests {

  override def timeLimit: Span = 60.seconds

  def runLog[A](s: Stream[IO, A]): Vector[A] =
    s.compile.toVector
      .unsafeRunTimed(timeLimit)
      .getOrElse(throw new TimeoutException("IO run timed out"))
}

trait JbokAsyncSpec extends AsyncWordSpec with Matchers with AsyncTimeLimitedTests {
  override def timeLimit: Span = 60.seconds

  implicit def futureUnitToFutureAssertion(fu: Future[Unit]): Future[Assertion] = fu.map(_ => Succeeded)

  implicit def ioUnitToFutureAssertion(iou: IO[Unit]): Future[Assertion] = iou.map(_ => Succeeded).unsafeToFuture()
}
