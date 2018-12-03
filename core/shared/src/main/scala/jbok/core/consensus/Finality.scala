package jbok.core.consensus

sealed trait Finality
object Finality {
  case object Never                    extends Finality
  case class BlockConfirmation(n: Int) extends Finality
}