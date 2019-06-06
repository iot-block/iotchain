package jbok.common

import jbok.common.math.N
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalacheck.Gen.Choose
import scodec.bits.ByteVector
import spire.laws.{gen => SpireGen}
import spire.math.{Natural, SafeLong}

object gen {
  def int(min: Int = Int.MinValue, max: Int = Int.MaxValue): Gen[Int] =
    Gen.choose(min, max)

  val hexChar: Gen[Char] = Gen.oneOf("0123456789abcdef")

  def hex(min: Int, max: Int): Gen[String] =
    for {
      size  <- Gen.chooseNum(min, max)
      chars <- Gen.listOfN(size, hexChar)
    } yield chars.mkString

  val natural: Gen[Natural] =
    SpireGen.natural

  val safeLong: Gen[SafeLong] =
    SpireGen.safeLong

  val N: Gen[N] =
    safeLong.map(_.abs)

  val bigInt: Gen[BigInt] =
    arbitrary[BigInt].map(_.abs)

  val byteArray: Gen[Array[Byte]] =
    Gen.listOf(arbitrary[Byte]).map(_.toArray)

  val byteVector: Gen[ByteVector] =
    byteArray.map(ByteVector.apply)

  def boundedByteArray(l: Int, u: Int): Gen[Array[Byte]] =
    Gen.choose(l, u).flatMap(size => sizedByteArray(size))

  def boundedByteVector(l: Int, u: Int): Gen[ByteVector] =
    Gen.choose(l, u).flatMap(size => sizedByteVector(size))

  def sizedByteArray(size: Int): Gen[Array[Byte]] =
    Gen.listOfN(size, arbitrary[Byte]).map(_.toArray)

  def sizedByteVector(size: Int): Gen[ByteVector] =
    Gen.listOfN(size, arbitrary[Byte]).map(ByteVector.apply)

  def posNum[T](implicit num: Numeric[T], c: Choose[T]): Gen[T] = Gen.posNum[T]

  def boundedList[T](minSize: Int, maxSize: Int, gen: Gen[T]): Gen[List[T]] =
    Gen.choose(minSize, maxSize).flatMap(size => Gen.listOfN(size, gen))
}
