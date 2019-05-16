package jbok.common.metrics

import cats.effect.IO
import io.prometheus.client.CollectorRegistry
import jbok.common.CommonSpec
import jbok.common.metrics.implicits._

import scala.concurrent.duration._

class PrometheusSpec extends CommonSpec {
  val registry: CollectorRegistry = new CollectorRegistry()

  implicit override val metrics: Metrics[IO] = Prometheus[IO](registry)

  "Prometheus" should {
    "time" in {
      val name = "important_fun"
      val ioa  = timer.sleep(1.second)
      ioa.timed(name).unsafeRunSync()

      IO.raiseError(new Exception("boom")).timed(name).attempt.unsafeRunSync()
      val report = Prometheus.textReport[IO](registry).unsafeRunSync()
      report.contains(s"""jbok_${name}_seconds_count{result="success",} 1.0""") shouldBe true
      report.contains(s"""jbok_${name}_seconds_count{result="failure",} 1.0""") shouldBe true
    }

    "gauge" in {
      val name = "running_programs"
      val ioa  = IO.raiseError(new Exception("boom"))
      ioa.gauged(name).attempt.unsafeRunSync()
      val report = Prometheus.textReport[IO](registry).unsafeRunSync()
      report.contains(s"""jbok_${name}_active 0.0""") shouldBe true
    }
  }
}
