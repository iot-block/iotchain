package jbok.common.metrics

import java.io.StringWriter

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.{CollectorRegistry, Gauge, Histogram, SimpleTimer}

object Prometheus {
  val METRIC_PREFIX = "jbok"
  val TIMER_SUFFIX  = "seconds"
  val GAUGE_SUFFIX  = "active"

  final case class MetricName(name: String) extends AnyVal

  def apply[F[_]](_registry: CollectorRegistry)(implicit F: Sync[F]): F[Metrics[F]] =
    for {
      histograms <- Ref.of[F, Map[MetricName, Histogram]](Map.empty)
      gauges     <- Ref.of[F, Map[MetricName, Gauge]](Map.empty)
    } yield
      new Metrics[F] {
        override type Registry = CollectorRegistry

        override val registry: Registry = _registry

        override def time(name: String, labels: List[String])(nanos: Long): F[Unit] =
          for {
            hist <- getOrCreateHistogram(name, labels.toSet)
            _ = hist.labels(labels: _*).observe(SimpleTimer.elapsedSecondsFromNanos(0, nanos))
          } yield ()

        override def gauge(name: String, labels: List[String])(delta: Double): F[Unit] =
          for {
            gauge <- getOrCreateGauge(name, labels.toSet)
            _ = gauge.inc(delta)
          } yield ()

        def histName(name: String): MetricName =
          MetricName(s"${METRIC_PREFIX}_${name}_${TIMER_SUFFIX}")

        def gaugeName(name: String): MetricName =
          MetricName(s"${METRIC_PREFIX}_${name}_${GAUGE_SUFFIX}")

        def getOrCreateHistogram(name: String, labels: Set[String]): F[Histogram] =
          histograms.modify(m => {
            val hn = histName(name)
            m.get(hn) match {
              case Some(hist) => m -> hist
              case None =>
                val hist = Histogram.build(hn.name, name).labelNames("result").register(registry)
                m + (hn -> hist) -> hist
            }
          })

        def getOrCreateGauge(name: String, labels: Set[String]): F[Gauge] =
          gauges.modify(m => {
            val gn = gaugeName(name)
            m.get(gn) match {
              case Some(gauge) => m -> gauge
              case None =>
                val gauge = Gauge.build(gn.name, name).register(registry)
                m + (gn -> gauge) -> gauge
            }
          })

      }

  def textReport[F[_]](registry: CollectorRegistry)(implicit F: Sync[F]): F[String] = F.delay {
    val writer = new StringWriter
    TextFormat.write004(writer, registry.metricFamilySamples)
    writer.toString
  }
}
