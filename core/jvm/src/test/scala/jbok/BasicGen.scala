package jbok
import java.math.BigInteger

import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.ByteVector

object BasicGen {
  val byteGen: Gen[Byte] = Gen.choose(Byte.MinValue, Byte.MaxValue)

  val shortGen: Gen[Short] = Gen.choose(Short.MinValue, Short.MaxValue)

  def intGen(min: Int, max: Int): Gen[Int] = Gen.choose(min, max)

  val intGen: Gen[Int] = Gen.choose(Int.MinValue, Int.MaxValue)

  val longGen: Gen[Long] = Gen.choose(Long.MinValue, Long.MaxValue)

  val bigIntGen: Gen[BigInt] = byteArrayOfNItemsGen(32).map(b => new BigInteger(1, b))

  val bigInt64Gen: Gen[BigInt] = byteArrayOfNItemsGen(64).map(b => new BigInteger(1, b))

  def randomSizeByteArrayGen(minSize: Int, maxSize: Int): Gen[Array[Byte]] =
    Gen.choose(minSize, maxSize).flatMap(byteArrayOfNItemsGen(_))

  def byteArrayOfNItemsGen(n: Int): Gen[Array[Byte]] = Gen.listOfN(n, Arbitrary.arbitrary[Byte]).map(_.toArray)

  def randomSizeByteStringGen(minSize: Int, maxSize: Int): Gen[ByteVector] =
    Gen.choose(minSize, maxSize).flatMap(byteVectorOfLengthNGen)

  def byteVectorOfLengthNGen(n: Int): Gen[ByteVector] = byteArrayOfNItemsGen(n).map(ByteVector.apply)

  def listByteStringOfNItemsGen(n: Int): Gen[List[ByteVector]] = Gen.listOf(byteVectorOfLengthNGen(n))

  def hexPrefixDecodeParametersGen(): Gen[(Array[Byte], Boolean)] =
    for {
      aByteList <- Gen.nonEmptyListOf(Arbitrary.arbitrary[Byte])
      t         <- Arbitrary.arbitrary[Boolean]
    } yield (aByteList.toArray, t)
}
