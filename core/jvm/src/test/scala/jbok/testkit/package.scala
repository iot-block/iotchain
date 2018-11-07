package jbok

import jbok.core.models.UInt256
import org.scalacheck.Arbitrary._
import org.scalacheck._

package object testkit {
  implicit val arbUint256 = Arbitrary(arbLong.arbitrary.map(x => UInt256(x)))
}
