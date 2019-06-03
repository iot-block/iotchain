package jbok.common

import spire.algebra._

import org.scalacheck.Arbitrary._
import org.scalacheck._
import spire.syntax.all._

object ArbitrarySupport {
  case class NonNegative[A](num: A)

  implicit def nonNegativeSpireImplicit[A: Signed: AdditiveGroup: Arbitrary]: Arbitrary[NonNegative[A]] =
    Arbitrary(arbitrary[A].map(_.abs).filter(_.signum > -1).map(NonNegative(_)))
}
