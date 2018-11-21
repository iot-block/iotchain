package jbok.crypto.bn256

object BN256 {
  def pair(g1: CurvePoint, g2: TwistPoint): Fp12 =
    OpTate.optimalAte(g2, g1)

  def pairingCheck(pairs: List[(CurvePoint, TwistPoint)]): Boolean = {
    val acc = pairs.indices.toList.foldLeft(Fp12.One) { (acc, i) =>
      if (pairs(i)._1.isInfinity || pairs(i)._2.isInfinity) {
        acc
      } else {
        acc * OpTate.miller(pairs(i)._2, pairs(i)._1)
      }
    }

    val ret = OpTate.finalExponentiation(acc)
    ret.isOne
  }
}
