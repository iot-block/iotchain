package jbok.common

import jbok.common.math.N
import org.scalacheck.Arbitrary
import scodec.bits.ByteVector

trait CommonArb {
  implicit val arbN: Arbitrary[N] =
    Arbitrary(gen.N)

  implicit val arbBigInt: Arbitrary[BigInt] =
    Arbitrary(gen.bigInt)

  implicit val arbByteVector: Arbitrary[ByteVector] =
    Arbitrary(gen.byteVector)
}

object CommonArb extends CommonArb
