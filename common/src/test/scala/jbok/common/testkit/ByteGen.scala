package jbok.common.testkit

import org.scalacheck.{Arbitrary, Gen}

object ByteGen extends ByteGen
trait ByteGen {
  def genBoundedBytes(minSize: Int, maxSize: Int): Gen[Array[Byte]] =
    Gen.choose(minSize, maxSize).flatMap { sz =>
      Gen.listOfN(sz, Arbitrary.arbitrary[Byte]).map(_.toArray)
    }

  def genBytes(size: Int): Gen[Array[Byte]] = genBoundedBytes(size, size)
}
