package jbok.common.metrics

import cats.effect.IO
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import jbok.JbokSpec
import fs2._
import scala.concurrent.duration._
import jbok.common.execution._
import cats.implicits._

class DropwizardSpec extends JbokSpec {
  def count(registry: MetricRegistry, counter: Counter): Option[Long] =
    Option(registry.getCounters.get(counter.value)).map(_.getCount)

  def count(registry: MetricRegistry, timer: Timer): Option[Long] =
    Option(registry.getTimers.get(timer.value)).map(_.getCount)

  def valuesOf(registry: MetricRegistry, timer: Timer): Option[List[Long]] =
    Option(registry.getTimers().get(timer.value)).map(_.getSnapshot.getValues.toList)

  case class Counter(value: String)
  case class Timer(value: String)

  "Dropwizard" should {
    val registry = SharedMetricRegistries.getOrCreate("test")
    val metric   = Dropwizard[IO](registry, "oho")

    "metric count" in {
      count(registry, Counter("oho.default.count")) shouldBe None
      metric.increaseCount(None).unsafeRunSync()
      count(registry, Counter("oho.default.count")) shouldBe Some(1L)

      count(registry, Counter("oho.aha.count")) shouldBe None
      metric.increaseCount(Some("aha")).unsafeRunSync()
      count(registry, Counter("oho.aha.count")) shouldBe Some(1L)
    }

    "metric time" in {
      valuesOf(registry, Timer("oho.default.time")) shouldBe None
      metric.recordTime(None, 100).unsafeRunSync()
      valuesOf(registry, Timer("oho.default.time")) shouldBe Some(List(100L))

      valuesOf(registry, Timer("oho.aha.time")) shouldBe None
      metric.recordTime(Some("aha"), 100).unsafeRunSync()
      valuesOf(registry, Timer("oho.aha.time")) shouldBe Some(List(100L))
    }

    "console reporter" in {
      val stream =
        Stream
          .range[IO](0, 10)
          .evalMap[IO, Unit](_ => T.sleep(500.millis) >> metric.increaseCount(None))
          .concurrently(Dropwizard.consoleReporter[IO](registry, 1.seconds))

      stream.compile.drain.unsafeRunSync()
    }
  }
}
