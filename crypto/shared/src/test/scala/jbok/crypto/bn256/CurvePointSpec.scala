package jbok.crypto.bn256

import jbok.JbokSpec

class CurvePointSpec extends JbokSpec {
  "curve point" should {
    val cp = CurvePoint.curveGen * BigInt("9876543210")
    "a + a = a * 2" in {
      val a2_1 = cp + cp
      val a2_2 = cp * 2
      val a2_3 = cp.double()
      a2_1 shouldBe a2_2
      a2_1 shouldBe a2_3
    }

    "a + a + a = a * 3 = a.double() + a" in {
      val a2_1 = cp + cp + cp
      val a2_2 = cp * 3
      val a2_3 = cp.double() + cp
      a2_1 shouldBe a2_2
      a2_1 shouldBe a2_3
    }
  }
}
