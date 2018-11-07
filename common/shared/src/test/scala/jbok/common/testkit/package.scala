package jbok.common
import org.scalacheck._
import org.scalacheck.Arbitrary._
import scodec.bits.ByteVector

package object testkit {
  implicit val arbByteVector = Arbitrary(arbString.arbitrary.map(x => ByteVector(x.getBytes)))
}
