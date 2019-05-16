package jbok

import cats.effect.IO
import org.scalatest._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import org.scalatest.time.Span

import scala.concurrent.Future
import scala.concurrent.duration._

trait JbokAsyncSpec extends AsyncWordSpec with Matchers with AsyncTimeLimitedTests {
  override def timeLimit: Span = 60.seconds

  implicit def futureUnitToFutureAssertion(fu: Future[Unit]): Future[Assertion] = fu.map(_ => Succeeded)

  implicit def ioUnitToFutureAssertion(iou: IO[Unit]): Future[Assertion] = iou.map(_ => Succeeded).unsafeToFuture()
}
