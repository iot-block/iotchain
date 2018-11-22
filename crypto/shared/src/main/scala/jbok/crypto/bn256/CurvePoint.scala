package jbok.crypto.bn256

import cats.implicits._
import Constants.p
object CurvePoint {
  val curveB   = BigInt(3)
  val curveGen = CurvePoint(BigInt(1), BigInt(2), BigInt(1), BigInt(1))

  def apply(x: Array[Byte], y: Array[Byte]): Option[CurvePoint] = {
    val a = BigInt(1, x)
    val b = BigInt(1, y)

    if (a == 0 && b == 0) {
      CurvePoint(0, 1, 0, 0).some
    } else {
      val r = CurvePoint(a, b, 1, 1)
      if (r.isOnCurve) r.some
      else None
    }
  }
}

case class CurvePoint(x: BigInt, y: BigInt, z: BigInt, t: BigInt) {
  def isOnCurve: Boolean =
    (y * y - x.pow(3)).mod(p) == CurvePoint.curveB

  def infinity: CurvePoint = this.copy(z = 0)

  def isInfinity: Boolean = z == 0

  def +(that: CurvePoint): CurvePoint =
    if (this.isInfinity) {
      that
    } else if (that.isInfinity) {
      this
    } else {
      val z1z1 = z * z
      val z2z2 = that.z * that.z
      val u1   = x * z2z2
      val u2   = that.x * z1z1
      val s1   = y * that.z * z2z2
      val s2   = that.y * z1z1 * z

      if (u1 == u2 && s1 == s2) {
        this.double()
      } else {
        // See http://hyperelliptic.org/EFD/g1p/auto-code/shortw/jacobian-0/addition/add-2007-bl.op3
        val h = u2 - u1
        val i = h * h * 4
        val j = h * i
        val r = (s2 - s1) * 2
        val v = u1 * i

        val rx = (r * r - j - v * 2) mod p
        val ry = (r * (v - rx) - (s1 * j) * 2) mod p
        val rz = (((z + that.z) * (z + that.z) - z1z1 - z2z2) * h) mod p

        CurvePoint(rx, ry, rz, t mod p)
      }
    }

  def double(): CurvePoint = {
    val a  = (x * x) mod p
    val b  = (y * y) mod p
    val c  = (b * b) mod p
    val d  = (((x + b) * (x + b) - a - c) * 2) mod p
    val e  = (a * 3) mod p
    val rx = (e * e - (d * 2)) mod p
    val ry = (e * (d - rx) - (c * 8)) mod p
    val rz = (y * z * 2) mod p

    CurvePoint(rx, ry, rz, t mod p)
  }

  def *(scalar: BigInt): CurvePoint =
    (0 to scalar.bitLength).toList.foldRight(infinity) { (i, r) =>
      val tt = r.double()
      if (scalar.testBit(i)) tt + this else tt
    }

  def makeAffine(): CurvePoint =
    if (z == 1) {
      this
    } else if (isInfinity) {
      CurvePoint(0, 1, 0, 0)
    } else {
      val zInv  = z.modInverse(p)
      val zInv2 = zInv * zInv mod p
      CurvePoint((x * zInv2) mod p, (y * zInv * zInv2) mod p, 1, 1)
    }

  def unary_-(): CurvePoint =
    CurvePoint(x, -y, z, BigInt(0))
}
