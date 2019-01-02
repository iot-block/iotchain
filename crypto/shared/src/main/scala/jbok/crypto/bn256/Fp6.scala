package jbok.crypto.bn256

import Constants.{xiTo2PMinus2Over3, xiTo2PSquaredMinus2Over3, xiToPMinus1Over3, xiToPSquaredMinus1Over3}
object Fp6 {
  val Zero: Fp6 = Fp6(Fp2.Zero, Fp2.Zero, Fp2.Zero)
  val One: Fp6  = Fp6(Fp2.Zero, Fp2.Zero, Fp2.One)
}

final case class Fp6(x: Fp2, y: Fp2, z: Fp2) {
  def isZero: Boolean = x.isZero && y.isZero && z.isZero

  def isOne: Boolean = x.isZero && y.isZero && z.isOne

  def unary_-(): Fp6 =
    Fp6(-x, -y, -z)

  def frobenius(): Fp6 =
    Fp6(
      x.conjugate() * xiTo2PMinus2Over3,
      y.conjugate() * xiToPMinus1Over3,
      z.conjugate()
    )

  // FrobeniusP2 computes (xτ²+yτ+z)^(p²) = xτ^(2p²) + yτ^(p²) + z
  def frobeniusP2(): Fp6 =
    // τ^(2p²) = τ²τ^(2p²-2) = τ²ξ^((2p²-2)/3)
    //    // τ^(p²) = ττ^(p²-1) = τξ^((p²-1)/3)
    Fp6(
      x * xiTo2PSquaredMinus2Over3,
      y * xiToPSquaredMinus1Over3,
      z
    )

  def +(that: Fp6): Fp6 =
    Fp6(x + that.x, y + that.y, z + that.z)

  def -(that: Fp6): Fp6 =
    Fp6(x - that.x, y - that.y, z - that.z)

  def double(): Fp6 =
    Fp6(x.double(), y.double(), z.double())

  def *(that: Fp6): Fp6 = {
    // "Multiplication and Squaring on Pairing-Friendly Fields"
    // Section 4, Karatsuba method.
    // http://eprint.iacr.org/2006/471.pdf
    val v0 = z * that.z
    val v1 = y * that.y
    val v2 = x * that.x

    val rz = ((x + y) * (that.x + that.y) - v1 - v2).mulXi() + v0
    val a  = y + z
    val ry = a * (that.y + that.z) - v0 - v1 + v2.mulXi()
    val rx = (x + z) * (that.x + that.z) - v0 + v1 - v2

    Fp6(rx, ry, rz)
  }

  def *(b: Fp2): Fp6 =
    Fp6(x * b, y * b, z * b)

  def *(b: BigInt): Fp6 =
    Fp6(x * b, y * b, z * b)

  def exp(power: BigInt): Fp6 =
    (0 until power.bitLength).toList.foldRight(Fp6.One) { (i, r) =>
      val t = r.square()
      if (power.testBit(i)) t * this else t
    }

  // MulTau computes τ·(aτ²+bτ+c) = bτ²+cτ+aξ
  def mulTau(): Fp6 =
    Fp6(y, z, x.mulXi())

  def square(): Fp6 = {
    val v0 = z.square()
    val v1 = y.square()
    val v2 = x.square()

    val rz = ((x + y).square() - v1 - v2).mulXi() + v0
    val ry = (z + y).square() - v0 - v1 + v2.mulXi()
    val rx = (x + z).square() - v0 + v1 - v2

    Fp6(rx, ry, rz)
  }

  def invert(): Fp6 = {
    // See "Implementing cryptographic pairings", M. Scott, section 3.2.
    // ftp://136.206.11.249/pub/crypto/pairings.pdf

    // Here we can give a short explanation of how it works: let j be a cubic root of
    // unity in GF(p²) so that 1+j+j²=0.
    // Then (xτ² + yτ + z)(xj²τ² + yjτ + z)(xjτ² + yj²τ + z)
    // = (xτ² + yτ + z)(Cτ²+Bτ+A)
    // = (x³ξ²+y³ξ+z³-3ξxyz) = F is an element of the base field (the norm).
    //
    // On the other hand (xj²τ² + yjτ + z)(xjτ² + yj²τ + z)
    // = τ²(y²-ξxz) + τ(ξx²-yz) + (z²-ξxy)
    //
    // So that's why A = (z²-ξxy), B = (ξx²-yz), C = (y²-ξxz)
    val A = z.square() - (x * y).mulXi()
    val B = x.square().mulXi() - (y * z)
    val C = y.square() - (x * z)
    val F = ((C * y).mulXi() + (A * z) + (B * x).mulXi()).invert()

    Fp6(C * F, B * F, A * F)
  }

}
