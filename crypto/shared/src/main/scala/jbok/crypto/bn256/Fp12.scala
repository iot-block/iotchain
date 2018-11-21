package jbok.crypto.bn256

import Constants.{xiToPMinus1Over6, xiToPSquaredMinus1Over6}

object Fp12 {
  val Zero: Fp12 = Fp12(Fp6.Zero, Fp6.Zero)
  val One: Fp12  = Fp12(Fp6.Zero, Fp6.One)
}
case class Fp12(x: Fp6, y: Fp6) {
  def isZero: Boolean = x.isZero && y.isZero

  def isOne: Boolean = x.isZero && y.isOne

  def conjugate(): Fp12 = Fp12(-x, y)

  def unary_-(): Fp12 = Fp12(-x, -y)

  def frobenius(): Fp12 = Fp12(x.frobenius() * xiToPMinus1Over6, y.frobenius())

  def frobeniusP2(): Fp12 = Fp12(x.frobeniusP2() * xiToPSquaredMinus1Over6, y.frobeniusP2())

  def +(that: Fp12): Fp12 = Fp12(x + that.x, y + that.y)

  def -(that: Fp12): Fp12 = Fp12(x - that.x, y - that.y)

  def *(that: Fp12): Fp12 =
    Fp12(x * that.y + that.x * y, y * that.y + (x * that.x).mulTau())

  def *(b: Fp6): Fp12 = Fp12(x * b, y * b)

  def exp(power: BigInt): Fp12 =
    (0 until power.bitLength).toList.foldRight(Fp12.One) { (i, r) =>
      val t = r.square()
      if (power.testBit(i)) t * this else t
    }

  def square(): Fp12 = {
    // Complex squaring algorithm
    val v0 = x * y
    val ty = (x + y) * (x.mulTau() + y) - v0 - v0.mulTau()

    Fp12(v0.double(), ty)
  }

  def invert(): Fp12 =
    // See "Implementing cryptographic pairings", M. Scott, section 3.2.
    // ftp://136.206.11.249/pub/crypto/pairings.pdf
    conjugate * (y.square() - x.square().mulTau()).invert()
}
