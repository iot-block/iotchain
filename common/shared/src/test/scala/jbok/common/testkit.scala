package jbok.common

import org.scalacheck._

import scala.concurrent.duration._

object testkit {
  def random[A](implicit arb: Arbitrary[A]): A =
    arb.arbitrary.sample.get

  def random[A](gen: Gen[A]): A =
    gen.sample.get

  def time[A](f: => A): (A, FiniteDuration) = {
    val start   = System.nanoTime
    val result  = f
    val elapsed = (System.nanoTime - start).nanos
    (result, elapsed)
  }

  def samples[A](gen: Gen[A]): Stream[Option[A]] =
    Stream.continually(gen.sample)

  def definedSamples[A](gen: Gen[A]): Stream[A] =
    samples(gen).flatMap { x =>
      x
    }
}
