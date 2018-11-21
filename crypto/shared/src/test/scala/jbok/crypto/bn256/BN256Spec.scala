package jbok.crypto.bn256

import jbok.JbokSpec

class BN256Spec extends JbokSpec {
  "fp2 invert" in {
    val a   = Fp2(BigInt("23423492374"), BigInt("12934872398472394827398470"))
    val inv = a.invert()
    val b   = inv * a

    b.isOne shouldBe true
  }

  "fp6 invert" in {
    val a = Fp6(
      Fp2(BigInt("239487238491"), BigInt("2356249827341")),
      Fp2(BigInt("82659782"), BigInt("182703523765")),
      Fp2(BigInt("978236549263"), BigInt("64893242"))
    )
    val inv = a.invert()
    val b   = inv * a

    b.isOne shouldBe true
  }

  "fp12 invert" in {
    val a = Fp12(
      Fp6(
        Fp2(BigInt("239846234862342323958623"), BigInt("2359862352529835623")),
        Fp2(BigInt("928836523"), BigInt("9856234")),
        Fp2(BigInt("235635286"), BigInt("5628392833"))
      ),
      Fp6(
        Fp2(BigInt("252936598265329856238956532167968"), BigInt("23596239865236954178968")),
        Fp2(BigInt("95421692834"), BigInt("236548")),
        Fp2(BigInt("924523"), BigInt("12954623"))
      )
    )
    val inv = a.invert()
    val b   = inv * a

    b.isOne shouldBe true
  }

  "curve" in {
    val g = CurvePoint(
      BigInt(1),
      BigInt(-2),
      BigInt(1),
      BigInt(0)
    )

    val x  = BigInt("32498273234")
    val gx = g * x
    val y  = BigInt("98732423523")
    val gy = g * y

    val s1 = (gx * y).makeAffine()
    val s2 = (gy * x).makeAffine()

    s1.x shouldBe s2.x
  }

  "order g1" in {
    val g = CurvePoint.curveGen * Constants.order
    g.isInfinity shouldBe true

    val one    = CurvePoint.curveGen * BigInt(1)
    val affine = (g + one).makeAffine()
    affine.x shouldBe one.x
    affine.y shouldBe one.y
  }

  "order g2" in {
    val g = TwistPoint.twistGen * Constants.order
    g.isInfinity shouldBe true

    val one    = TwistPoint.twistGen * BigInt(1)
    val affine = (g + one).makeAffine()
    affine.x shouldBe one.x
    affine.y shouldBe one.y
  }

  "order gt" in {
    val gt = BN256.pair(CurvePoint.curveGen, TwistPoint.twistGen)
    val g  = gt.exp(Constants.order)
    g.isOne shouldBe true
  }

  "g1 identity" in {
    val g = CurvePoint.curveGen * BigInt(0)
    g.isInfinity shouldBe true
  }

  "g2 identity" in {
    val g = TwistPoint.twistGen * BigInt(0)
    g.isInfinity shouldBe true
  }

  "tripartite diffie hellman" in {
    val a = BigInt(123)
    val b = BigInt(456)
    val c = BigInt(789)

    val pa = CurvePoint.curveGen * a
    val qa = TwistPoint.twistGen * a
    val pb = CurvePoint.curveGen * b
    val qb = TwistPoint.twistGen * b
    val pc = CurvePoint.curveGen * c
    val qc = TwistPoint.twistGen * c

    val k1 = BN256.pair(pb, qc).exp(a)
    val k2 = BN256.pair(pc, qa).exp(b)
    val k3 = BN256.pair(pa, qb).exp(c)

    k1 shouldBe k2
    k2 shouldBe k3
  }
}
