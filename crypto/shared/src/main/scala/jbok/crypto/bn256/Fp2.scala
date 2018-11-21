package jbok.crypto.bn256

import Constants.p

object Fp2 {
  val Zero: Fp2 = Fp2(0, 0)
  val One: Fp2  = Fp2(0, 1)
}

case class Fp2(x: BigInt, y: BigInt) {
  def isZero: Boolean =
    (x == 0) && (y == 0)

  def isOne: Boolean =
    (x == 0) && (y == 1)

  def conjugate(): Fp2 =
    Fp2(-x mod p, y)

  def unary_-(): Fp2 =
    Fp2(-x mod p, -y mod p)

  def +(that: Fp2): Fp2 =
    Fp2((x + that.x) mod p, (y + that.y) mod p)

  def -(that: Fp2): Fp2 =
    Fp2((x - that.x) mod p, (y - that.y) mod p)

  def double(): Fp2 =
    Fp2((x * 2) mod p, (y * 2) mod p)

  def exp(power: BigInt): Fp2 =
    (0 until power.bitLength).toList.foldRight(Fp2.One) { (i, r) =>
      val t = r.square()
      if (power.testBit(i)) t * this else t
    }

  // See "Multiplication and Squaring in Pairing-Friendly Fields",
  // http://eprint.iacr.org/2006/471.pdf
  def *(that: Fp2): Fp2 = {
    val rx = (x * that.y + that.x * y) mod p
    val ry = (y * that.y - x * that.x) mod p

    Fp2(rx, ry)
  }

  def *(b: BigInt): Fp2 = {
    val rx = (x * b) mod p
    val ry = (y * b) mod p

    Fp2(rx, ry)
  }

  // MulXi sets e=ξa where ξ=i+9 and then returns e.
  def mulXi(): Fp2 = {
    // (xi+y)(i+9) = (9x+y)i+(9y-x)
    val rx = (9 * x + y) mod p
    val ry = (9 * y - x) mod p

    Fp2(rx, ry)
  }

  def square(): Fp2 = {
    // Complex squaring algorithm:
    // (xi+b)² = (x+y)(y-x) + 2*i*x*y
    val rx = (2 * x * y) mod p
    val ry = ((x + y) * (y - x)) mod p

    Fp2(rx, ry)
  }

  def invert(): Fp2 = {
    // See "Implementing cryptographic pairings", M. Scott, section 3.2.
    // ftp://136.206.11.249/pub/crypto/pairings.pdf
    val inv = (x * x + y * y).modInverse(p)
    val rx  = (-x * inv) mod p
    val ry  = (y * inv) mod p

    Fp2(rx, ry)
  }
}
