package jbok.common

import org.scalacheck._

object testkit {
  def random[A](implicit arb: Arbitrary[A]): A =
    arb.arbitrary.sample.get

  def random[A](gen: Gen[A]): A =
    gen.sample.get
}
