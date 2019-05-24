package jbok.common.metrics

import java.io.StringWriter

import cats.effect.Sync
import cats.implicits._
import io.prometheus.client._
import io.prometheus.client.exporter.common.TextFormat

final class PrometheusMetrics[F[_]](val registry: CollectorRegistry = PrometheusMetrics.registry)(implicit F: Sync[F]) extends Metrics[F] {
  import PrometheusMetrics._

  override type Registry = CollectorRegistry

  override def acc(name: String, labels: String*)(n: Double): F[Unit] =
    for {
      gauge <- getOrCreateGauge(name, labels: _*)
      _ = gauge.labels(labels: _*).inc(n)
    } yield ()

  override def inc(name: String, labels: String*)(n: Double): F[Unit] =
    for {
      gauge <- getOrCreateGauge(name, labels: _*)
      _ = gauge.labels(labels: _*).inc(n)
    } yield ()

  override def dec(name: String, labels: String*)(n: Double): F[Unit] =
    for {
      gauge <- getOrCreateGauge(name, labels: _*)
      _ = gauge.labels(labels: _*).dec(n)
    } yield ()

  override def set(name: String, labels: String*)(n: Double): F[Unit] =
    for {
      gauge <- getOrCreateGauge(name, labels: _*)
      _ = gauge.labels(labels: _*).set(n)
    } yield ()

  override def observe(name: String, labels: String*)(n: Double): F[Unit] =
    for {
      hist <- getOrCreateHistogram(name, labels: _*)
      _ = hist.labels(labels: _*).observe(SimpleTimer.elapsedSecondsFromNanos(0, n.toLong))
    } yield ()

  def histName(name: String): String =
    s"${METRIC_PREFIX}_${name}_${TIMER_SUFFIX}"

  def gaugeName(name: String): String =
    s"${METRIC_PREFIX}_${name}_${GAUGE_SUFFIX}"

  def getOrCreateHistogram(name: String, labels: String*): F[Histogram] =
    for {
      metricName <- histName(name).pure[F]
      hist <- histograms.get(metricName) match {
        case Some(hist) => F.pure(hist)
        case None =>
          F.delay(Histogram.build(metricName, name).labelNames(labels: _*).register(registry))
            .attempt
            .flatMap {
              case Left(_) =>
                getOrCreateHistogram(name, labels: _*)
              case Right(hist) =>
                F.delay(histograms += (metricName -> hist)).as(hist)
            }
      }
    } yield hist

  def getOrCreateGauge(name: String, labels: String*): F[Gauge] =
    for {
      metricName <- gaugeName(name).pure[F]
      gauge <- gauges.get(metricName) match {
        case Some(gauge) => F.pure(gauge)
        case None =>
          F.delay(Gauge.build(metricName, name).labelNames(labels: _*).register(registry))
            .attempt
            .flatMap {
              case Left(_) =>
                getOrCreateGauge(name, labels: _*)
              case Right(gauge) =>
                F.delay(gauges += (metricName -> gauge)).as(gauge)
            }
      }
    } yield gauge
}

object PrometheusMetrics {
  lazy val registry = new CollectorRegistry()

  val METRIC_PREFIX = "jbok"
  val TIMER_SUFFIX  = "seconds"
  val GAUGE_SUFFIX  = "active"

  private var histograms: Map[String, Histogram] = Map.empty

  private var gauges: Map[String, Gauge] = Map.empty

  def textReport[F[_]](registry: CollectorRegistry = registry)(implicit F: Sync[F]): F[String] = F.delay {
    val writer = new StringWriter
    TextFormat.write004(writer, registry.metricFamilySamples)
    writer.toString
  }
}
