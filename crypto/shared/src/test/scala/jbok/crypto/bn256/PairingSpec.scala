package jbok.crypto.bn256

import jbok.common.CommonSpec

class PairingSpec extends CommonSpec {
  "pairing" in {
    val a1  = CurvePoint.curveGen * BigInt(1)
    val a2  = CurvePoint.curveGen * BigInt(2)
    val a37 = CurvePoint.curveGen * BigInt(37)
    val an1 = CurvePoint.curveGen * BigInt(
      "21888242871839275222246405745257275088548364400416034343698204186575808495616")

    val b0   = TwistPoint.twistGen * BigInt(0)
    val b1   = TwistPoint.twistGen * BigInt(1)
    val b2   = TwistPoint.twistGen * BigInt(2)
    val b27  = TwistPoint.twistGen * BigInt(27)
    val b999 = TwistPoint.twistGen * BigInt(999)
    val bn1 = TwistPoint.twistGen * BigInt(
      "21888242871839275222246405745257275088548364400416034343698204186575808495616")

    val p1  = BN256.pair(a1, b1)
    val pn1 = BN256.pair(a1, bn1)
    val np1 = BN256.pair(an1, b1)

    pn1 shouldBe np1
    BN256.pairingCheck(List[(CurvePoint, TwistPoint)]((a1, b1), (an1, b1))) shouldBe true

    val p0   = p1 * pn1
    val p0_2 = BN256.pair(a1, b0)
    p0 shouldBe p0_2

    val p0_3 = p1.exp(BigInt("21888242871839275222246405745257275088548364400416034343698204186575808495617"))
    p0_3 shouldBe p0

    val p2   = BN256.pair(a2, b1)
    val p2_2 = BN256.pair(a1, b2)
    val p2_3 = p1.exp(BigInt(2))
    p2 shouldBe p2_2
    p2 shouldBe p2_3
    p2 == p1 shouldBe false

    BN256.pairingCheck(List[(CurvePoint, TwistPoint)]((a1, b1), (a1, b1))) shouldBe false

    val p999   = BN256.pair(a37, b27)
    val p999_2 = BN256.pair(a1, b999)
    p999 shouldBe p999_2
  }

}
