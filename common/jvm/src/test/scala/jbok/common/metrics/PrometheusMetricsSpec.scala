package jbok.common.metrics

import cats.effect.{IO, Resource}
import jbok.common.CommonSpec
import jbok.common.metrics.implicits._

import scala.concurrent.duration._

class PrometheusMetricsSpec extends CommonSpec {
  implicit val prometheus = new PrometheusMetrics[IO]()

  "Prometheus" should {
    "observed" in {
      val name = "important_fun"
      val ioa  = timer.sleep(1.second)
      ioa.observed(name).unsafeRunSync()

      IO.raiseError(new Exception("boom")).observed(name).attempt.unsafeRunSync()
      val report = PrometheusMetrics.textReport[IO]().unsafeRunSync()
      report.contains(s"""jbok_${name}_seconds_count{result="success",} 1.0""") shouldBe true
      report.contains(s"""jbok_${name}_seconds_count{result="failure",} 1.0""") shouldBe true
    }

    "monitored" in {
      val name = "running_programs"
      val res  = Resource.liftF(IO.raiseError[String](new Exception("boom")))
      res
        .monitored(name)
        .use { _ =>
          IO.unit
        }
        .attempt
        .unsafeRunSync()
      val report = PrometheusMetrics.textReport[IO]().unsafeRunSync()
      report.contains(s"""jbok_${name}_active 0.0""") shouldBe true
    }
  }
}
