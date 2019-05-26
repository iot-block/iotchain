package jbok.persistent

import org.scalacheck.{Arbitrary, Gen}

object testkit {
  implicit def arbColumnFamily: Arbitrary[ColumnFamily] = Arbitrary {
    Gen.alphaNumStr.map(ColumnFamily.apply)
  }
}
