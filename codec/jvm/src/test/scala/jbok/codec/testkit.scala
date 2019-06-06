package jbok.codec

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import org.scalatest.{Assertion, Matchers}
import scodec.bits.{BitVector, ByteVector}
import scodec.{Attempt, DecodeResult, Err}

object testkit extends testkit
trait testkit extends Matchers {
  def roundtripAndMatch[A](a: A, expected: ByteVector)(implicit c: RlpCodec[A]): Assertion = {
    roundtrip[A](a)
    a.encoded.bits.bytes shouldBe expected
  }

  def roundtripLen[A](a: A, expectedNumBytes: Int)(implicit c: RlpCodec[A]): Assertion = {
    roundtrip[A](a)
    a.encoded.bits.bytes.length shouldBe expectedNumBytes
  }

  def roundtrip[A](a: A)(implicit c: RlpCodec[A]): Assertion =
    roundtrip(c, a)

  def roundtrip[A](codec: RlpCodec[A], value: A): Assertion = {
    val encoded = codec.encode(value)
    encoded.isSuccessful shouldBe true
    val Attempt.Successful(DecodeResult(decoded, remainder)) = codec.decode(encoded.require)
    remainder shouldBe BitVector.empty
    decoded shouldBe value
  }

  def roundtripAll[A](codec: RlpCodec[A], as: collection.Iterable[A]): Unit =
    as foreach { a =>
      roundtrip(codec, a)
    }

  def encodeError[A](codec: RlpCodec[A], a: A, err: Err): Assertion = {
    val encoded = codec.encode(a)
    encoded shouldBe Attempt.Failure(err)
  }

  def shouldDecodeFullyTo[A](codec: RlpCodec[A], buf: BitVector, expected: A): Assertion = {
    val Attempt.Successful(DecodeResult(actual, rest)) = codec decode buf
    rest shouldBe BitVector.empty
    actual shouldBe expected
  }
}
