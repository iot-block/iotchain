package jbok.common.metrics

import java.io.StringWriter

import cats.effect.Sync
import cats.implicits._
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.{CollectorRegistry, Gauge, Histogram, SimpleTimer}

object Prometheus {
  val METRIC_PREFIX = "jbok"
  val TIMER_SUFFIX  = "seconds"
  val GAUGE_SUFFIX  = "active"

  final case class MetricName(name: String) extends AnyVal

  private var histograms: Map[MetricName, Histogram] = Map.empty
  private var gauges: Map[MetricName, Gauge]         = Map.empty

  def apply[F[_]](_registry: CollectorRegistry)(implicit F: Sync[F]): Metrics[F] =
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

      override def current(name: String, labels: String*)(current: Double): F[Unit] =
        for {
          gauge <- getOrCreateGauge(name, labels.toSet)
          _ = gauge.set(current)
        } yield ()

      def histName(name: String): MetricName =
        MetricName(s"${METRIC_PREFIX}_${name}_${TIMER_SUFFIX}")

      def gaugeName(name: String): MetricName =
        MetricName(s"${METRIC_PREFIX}_${name}_${GAUGE_SUFFIX}")

      def getOrCreateHistogram(name: String, labels: Set[String]): F[Histogram] = F.delay {
        val hn = histName(name)
        histograms.get(hn) match {
          case Some(hist) => hist
          case None =>
            synchronized {
              val hist = Histogram.build(hn.name, name).labelNames("result").register(registry)
              histograms += (hn -> hist)
              hist
            }
        }
      }

      def getOrCreateGauge(name: String, labels: Set[String]): F[Gauge] = F.delay {
        val gn = gaugeName(name)
        gauges.get(gn) match {
          case Some(gauge) => gauge
          case None =>
            synchronized {
              val gauge = Gauge.build(gn.name, name).register(registry)
              gauges += (gn -> gauge)
              gauge
            }
        }
      }
    }

  def textReport[F[_]](registry: CollectorRegistry)(implicit F: Sync[F]): F[String] = F.delay {
    val writer = new StringWriter
    TextFormat.write004(writer, registry.metricFamilySamples)
    writer.toString
  }
}