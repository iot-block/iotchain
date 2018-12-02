package jbok.common.metrics

/** algebra for writing metrics to a metrics registry(e.g. `Dropwizard` or `Prometheus`) */
trait MetricsOps[F[_]] {

  /** Increases the count for some classifier */
  def increaseCount(classifier: Option[String]): F[Unit]

  /** Decreases the count for some classifier */
  def decreaseCount(classifier: Option[String]): F[Unit]

  /** Records the time for some classifier */
  def recordTime(classifier: Option[String], elapsed: Long): F[Unit]

  /** Record abnormal, like errors, timeouts or just other abnormal terminations */
  def recordAbnormalTermination(
      classifier: Option[String],
      elapsed: Long,
      terminationType: TerminationType
  ): F[Unit]

  /** Describes the type of abnormal termination*/
  sealed trait TerminationType

  object TerminationType {
    case object Abnormal extends TerminationType
    case object Error    extends TerminationType
    case object Timeout  extends TerminationType
  }
}
