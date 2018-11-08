package jbok.common

import java.math.BigInteger

import org.scalacheck._
import org.scalacheck.Arbitrary._
import scodec.bits.ByteVector

object testkit {
  def intGen(min: Int, max: Int): Gen[Int] = Gen.choose(min, max)

  implicit val arbInt: Arbitrary[Int] = Arbitrary { intGen(Int.MinValue, Int.MaxValue) }

  implicit val arbByteVector = Arbitrary(arbString.arbitrary.map(x => ByteVector(x.getBytes)))

  def genBoundedBytes(minSize: Int, maxSize: Int): Gen[Array[Byte]] =
    Gen.choose(minSize, maxSize).flatMap { sz =>
      Gen.listOfN(sz, Arbitrary.arbitrary[Byte]).map(_.toArray)
    }

  def genBoundedByteVector(minSize: Int, maxSize: Int): Gen[ByteVector] =
    genBoundedBytes(minSize, maxSize).map(arr => ByteVector(arr))

  private def charGen: Gen[Char] = Gen.oneOf("0123456789abcdef")

  def genHex(min: Int, max: Int): Gen[String] =
    for {
      size  <- Gen.chooseNum(min, max)
      chars <- Gen.listOfN(size, charGen)
    } yield chars.mkString

  val bigIntGen: Gen[BigInt] = genBoundedBytes(32, 32).map(b => new BigInteger(1, b))

  val bigInt64Gen: Gen[BigInt] = genBoundedBytes(64, 64).map(b => new BigInteger(1, b))
}
