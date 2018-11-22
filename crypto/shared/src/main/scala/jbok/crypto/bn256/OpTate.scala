package jbok.crypto.bn256

import Constants.{u, xiToPMinus1Over2, xiToPMinus1Over3, xiToPSquaredMinus1Over3}

// Fp12: square(x) almost 45% faster than x * x

object OpTate {
  def Add(r: TwistPoint, p: TwistPoint, q: CurvePoint, r2: Fp2): (Fp2, Fp2, Fp2, TwistPoint) = {
    val h  = p.x * r.t - r.x
    val i  = h * h
    val e  = (i + i) * 2
    val j  = h * e
    val l1 = ((p.y + r.z).square() - r2 - r.t) * r.t - r.y - r.y
    val v  = r.x * e

    val rx = l1 * l1 - j - v - v
    val ry = (v - rx) * l1 - (r.y * j).double()
    val rz = (r.z + h).square() - r.t - i
    val rt = rz.square()

    val a          = (l1 * p.x).double() - ((p.y + rz).square() - r2 - rt)
    val c          = (rz * q.y).double()
    val rb         = (-l1 * q.x).double()
    val twistPoint = TwistPoint(rx, ry, rz, rt)
    (a, rb, c, twistPoint)
  }

  def double(r: TwistPoint, q: CurvePoint): (Fp2, Fp2, Fp2, TwistPoint) = {
    // See the doubling algorithm for a=0 from "Faster Computation of the
    // Tate Pairing", http://arxiv.org/pdf/0904.0854v3.pdf
    val a  = r.x.square()
    val b  = r.y.square()
    val c  = b.square()
    val d  = ((r.x + b).square() - a - c).double()
    val e  = a * 3
    val g  = e.square()
    val rx = g - d.double()
    val rz = (r.y + r.z).square() - b - r.t
    val ry = (d - rx) * e - (c * 8)
    val rt = rz.square()

    val ra = (r.x + e).square() - a - g - (b * 4)
    val rb = (e * r.t).double().unary_-() * q.x
    val rc = (rz * r.t).double() * q.y
    (ra, rb, rc, TwistPoint(rx, ry, rz, rt))
  }

  def mulLine(in: Fp12, a: Fp2, b: Fp2, c: Fp2): Fp12 = {
    val x  = Fp6(Fp2.Zero, a, b) * in.x
    val y  = Fp6(Fp2.Zero, a, b + c)
    val t  = in.y * c
    val rx = (in.x + in.y) * y - t - x
    val ry = t + x.mulTau()

    Fp12(rx, ry)
  }

  // sixuPlus2NAF is 6u+2 in non-adjacent form.
  private val sixuPlus2NAF =
    List[Int](0, 0, 0, 1, 0, 1, 0, -1, 0, 0, 1, -1, 0, 0, 1, 0, 0, 1, 1, 0, -1, 0, 0, 1, 0, -1, 0, 0, 0, 0, 1, 1, 1, 0,
      0, -1, 0, 0, 1, 0, 0, 0, 0, 0, -1, 0, 0, 1, 1, 0, 0, -1, 0, 0, 0, 1, 1, 0, -1, 0, 0, 1, 0, 1, 1)

  // miller implements the Miller loop for calculating the Optimal Ate pairing.
  // See algorithm 1 from http://cryptojedi.org/papers/dclxvi-20100714.pdf
  def miller(q: TwistPoint, p: CurvePoint): Fp12 = {
    val aAffine = q.makeAffine()
    val bAffine = p.makeAffine()

    val minusA = -aAffine
    val y2     = aAffine.y.square()

    val (rett, rt) = (1 until sixuPlus2NAF.length).toList.foldRight((Fp12.One, aAffine)) {
      case (i, (fp12, ri)) =>
        val (a, b, c, r) = double(ri, bAffine)
        val tmp = if (i != (sixuPlus2NAF.length - 1)) {
          fp12.square()
        } else {
          fp12
        }

        val ret = mulLine(tmp, a, b, c)
        sixuPlus2NAF(i - 1) match {
          case 1 =>
            val (aa, bb, cc, rr) = Add(r, aAffine, bAffine, y2)
            (mulLine(ret, aa, bb, cc), rr)
          case -1 =>
            val (aa, bb, cc, rr) = Add(r, minusA, bAffine, y2)
            (mulLine(ret, aa, bb, cc), rr)
          case _ =>
            (ret, r)
        }
    }

    // In order to calculate Q1 we have to convert q from the sextic twist
    // to the full GF(p^12) group, apply the Frobenius there, and convert
    // back.
    //
    // The twist isomorphism is (x', y') -> (xω², yω³). If we consider just
    // x for a moment, then after applying the Frobenius, we have x̄ω^(2p)
    // where x̄ is the conjugate of x. If we are going to apply the inverse
    // isomorphism we need a value with a single coefficient of ω² so we
    // rewrite this as x̄ω^(2p-2)ω². ξ⁶ = ω and, due to the construction of
    // p, 2p-2 is a multiple of six. Therefore we can rewrite as
    // x̄ξ^((p-1)/3)ω² and applying the inverse isomorphism eliminates the
    // ω².
    //
    // A similar argument can be made for the y value.
    val q1 =
      TwistPoint(aAffine.x.conjugate() * xiToPMinus1Over3, aAffine.y.conjugate() * xiToPMinus1Over2, Fp2.One, Fp2.One)

    // For Q2 we are applying the p² Frobenius. The two conjugations cancel
    // out and we are left only with the factors from the isomorphism. In
    // the case of x, we end up with a pure number which is why
    // xiToPSquaredMinus1Over3 is ∈ GF(p). With y we get a factor of -1. We
    // ignore this to end up with -Q2.
    val minusQ2         = TwistPoint(aAffine.x * xiToPSquaredMinus1Over3, aAffine.y, Fp2.One, Fp2.One)
    val (a, b, c, newR) = Add(rt, q1, bAffine, q1.y.square())
    val (aa, bb, cc, _) = Add(newR, minusQ2, bAffine, minusQ2.y.square())
    mulLine(mulLine(rett, a, b, c), aa, bb, cc)
  }

  // finalExponentiation computes the (p¹²-1)/Order-th power of an element of
  // GF(p¹²) to obtain an element of GT (steps 13-15 of algorithm 1 from
  // http://cryptojedi.org/papers/dclxvi-20100714.pdf)
  def finalExponentiation(in: Fp12): Fp12 = {
    // This is the p^6-Frobenius
    val inv = in.invert()
    val t1  = in.conjugate() * inv
    val t2  = t1.frobeniusP2() * t1

    val fp  = t2.frobenius()
    val fp2 = t2.frobeniusP2()
    val fp3 = fp2.frobenius()

    val fu  = t2.exp(u)
    val fu2 = fu.exp(u)
    val fu3 = fu2.exp(u)

    val fup  = fu.frobenius()
    val fu2p = fu2.frobenius()
    val fu3p = fu3.frobenius()

    val y0 = fp * fp2 * fp3
    val y1 = t2.conjugate()
    val y2 = fu2.frobeniusP2()
    val y3 = fup.conjugate()
    val y4 = (fu * fu2p).conjugate()
    val y5 = fu2.conjugate()
    val y6 = (fu3 * fu3p).conjugate()

    val h0 = y6.square() * y4 * y5
    val h1 = y3 * y5 * h0
    val h2 = ((h0 * y2) * h1.square()).square()
    (h2 * y1).square() * (h2 * y0)
  }

  def optimalAte(a: TwistPoint, b: CurvePoint): Fp12 =
    if (a.isInfinity || b.isInfinity) {
      Fp12.One
    } else {
      val e = miller(a, b)
      finalExponentiation(e)
    }

}
