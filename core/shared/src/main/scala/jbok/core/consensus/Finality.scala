package jbok.core.consensus

sealed trait Finality
object Finality {
  final case object Never                    extends Finality
  final case class BlockConfirmation(n: Int) extends Finality
}