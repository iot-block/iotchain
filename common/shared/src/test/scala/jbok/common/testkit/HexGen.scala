package jbok.common.testkit

import org.scalacheck.Gen

object HexGen {
  private def charGen: Gen[Char] = Gen.oneOf("0123456789abcdef")

  def genHex(min: Int, max: Int): Gen[String] =
    for {
      size <- Gen.chooseNum(min, max)
      chars <- Gen.listOfN(size, charGen)
    } yield chars.mkString
}
