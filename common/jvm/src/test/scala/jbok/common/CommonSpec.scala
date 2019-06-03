package jbok.common

import cats.effect.{IO, Resource}
import jbok.common.log.{Level, Logger}
import jbok.common.thread.ThreadUtil
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest._
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.prop.PropertyChecks
import org.scalatest.time.Span

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait CommonSpec
    extends WordSpecLike
    with Matchers
    with PropertyChecks
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with TimeLimitedTests
    with CancelAfterFailure
    with CommonArb {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  implicit val timer = IO.timer(ExecutionContext.global)

  implicit val ce = IO.ioConcurrentEffect(cs)

  implicit val acg = ThreadUtil.acgGlobal

  override def timeLimit: Span = 60.seconds

  Logger.setRootHandlers[IO](Logger.consoleHandler(minimumLevel = Some(Level.Info))).unsafeRunSync()

  def withResource[A](res: Resource[IO, A])(f: A => IO[Unit]): Unit =
    res.use(a => f(a)).unsafeRunSync()

  def random[A](implicit arb: Arbitrary[A]): A =
    arb.arbitrary.sample.get

  def random[A](gen: Gen[A]): A =
    gen.sample.get
}

object CommonSpec extends CommonSpec
