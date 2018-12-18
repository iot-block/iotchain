package jbok.common.metrics

import cats.effect.IO
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.metrics.implicits._

import scala.concurrent.duration._

class DropwizardSpec extends JbokSpec {
  def count(registry: MetricRegistry, counter: Counter): Option[Long] =
    Option(registry.getCounters.get(counter.value)).map(_.getCount)

  def count(registry: MetricRegistry, timer: Timer): Option[Long] =
    Option(registry.getTimers.get(timer.value)).map(_.getCount)

  def valuesOf(registry: MetricRegistry, timer: Timer): Option[List[Long]] =
    Option(registry.getTimers().get(timer.value)).map(_.getSnapshot.getValues.toList)

  case class Counter(value: String)
  case class Timer(value: String)

  val registry        = SharedMetricRegistries.getOrCreate("test")
  implicit val metrics: Metrics[IO] = Dropwizard[IO](registry)

  "Dropwizard" should {
    "time" in {
      valuesOf(registry, Timer("ioa")) shouldBe None
      val ioa = T.sleep(1.second)
      ioa.timed("ioa").unsafeRunSync()
      valuesOf(registry, Timer("ioa")).isDefined shouldBe true
    }

    "console reporter" in {
      val stream =
        Stream
          .range[IO](0, 20)
          .evalMap[IO, Unit](_ => T.sleep(100.millis).timed("T.sleep"))
          .concurrently(Dropwizard.consoleReporter[IO](registry, 1.seconds))

      stream.compile.drain.unsafeRunSync()
    }
  }
}
