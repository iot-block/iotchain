package jbok.codec

import jbok.codec.rlp.RlpCodec
import org.scalacheck.Gen
import org.scalatest.Matchers
import scodec.bits.{BitVector, ByteVector}
import scodec.{Attempt, DecodeResult, Err}

import scala.concurrent.duration.{FiniteDuration, _}

object testkit extends testkit
trait testkit extends Matchers {
  def roundtripAndMatch[A](a: A, expected: ByteVector)(implicit c: RlpCodec[A]) = {
    roundtrip[A](a)
    c.encode(a).require.bytes shouldBe expected
  }

  def roundtripLen[A](a: A, expectedLen: Int)(implicit c: RlpCodec[A]) = {
    roundtrip[A](a)
    c.encode(a).require.bytes.length shouldBe expectedLen
  }

  def roundtrip[A](a: A)(implicit c: RlpCodec[A]): Unit =
    roundtrip(c, a)

  def roundtrip[A](codec: RlpCodec[A], value: A): Unit = {
    val encoded = codec.encode(value)
    encoded.isSuccessful shouldBe true
    val Attempt.Successful(DecodeResult(decoded, remainder)) = codec.decode(encoded.require)
    remainder shouldEqual BitVector.empty
    decoded shouldEqual value
    ()
  }

  def roundtripAll[A](codec: RlpCodec[A], as: collection.Iterable[A]): Unit =
    as foreach { a =>
      roundtrip(codec, a)
    }

  def encodeError[A](codec: RlpCodec[A], a: A, err: Err) = {
    val encoded = codec.encode(a)
    encoded shouldBe Attempt.Failure(err)
  }

  def shouldDecodeFullyTo[A](codec: RlpCodec[A], buf: BitVector, expected: A) = {
    val Attempt.Successful(DecodeResult(actual, rest)) = codec decode buf
    rest shouldBe BitVector.empty
    actual shouldBe expected
  }

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
