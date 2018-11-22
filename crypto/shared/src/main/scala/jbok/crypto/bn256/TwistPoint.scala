package jbok.crypto.bn256

import cats.implicits._

object TwistPoint {
  val twistB = Fp2(
    BigInt("266929791119991161246907387137283842545076965332900288569378510910307636690"),
    BigInt("19485874751759354771024239261021720505790618469301721065564631296452457478373")
  )

  val twistGen = TwistPoint(
    Fp2(
      BigInt("11559732032986387107991004021392285783925812861821192530917403151452391805634"),
      BigInt("10857046999023057135944570762232829481370756359578518086990519993285655852781")
    ),
    Fp2(
      BigInt("4082367875863433681332203403145435568316851327593401208105741076214120093531"),
      BigInt("8495653923123431417604973247489272438418190587263600148770280649306958101930")
    ),
    Fp2.One,
    Fp2.One
  )

  def apply(a: Array[Byte], b: Array[Byte], c: Array[Byte], d: Array[Byte]): Option[TwistPoint] = {
    val x = Fp2(BigInt(1, a), BigInt(1, b))
    val y = Fp2(BigInt(1, c), BigInt(1, d))

    if (x.isZero && y.isZero) {
      TwistPoint(Fp2.Zero, Fp2.One, Fp2.Zero, Fp2.Zero).some
    } else {
      val t = TwistPoint(x, y, Fp2.One, Fp2.One)
      if (t.isOnCurve) t.some else None
    }
  }

}

case class TwistPoint(x: Fp2, y: Fp2, z: Fp2, t: Fp2) {
  def isOnCurve: Boolean =
    (y.square - x.square() * x) == TwistPoint.twistB

  def isInfinity: Boolean = z.isZero

  // For additional comments, see the same function in curve.
  def +(that: TwistPoint): TwistPoint =
    if (this.isInfinity) {
      that
    } else if (that.isInfinity) {
      this
    } else {
      val z1z1 = z.square()
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

        val rx = r * r - j - (v * 2)
        val ry = r * (v - rx) - ((s1 * j) * 2)
        val rz = ((z + that.z) * (z + that.z) - z1z1 - z2z2) * h

        TwistPoint(rx, ry, rz, t)
      }
    }

  def double(): TwistPoint = {
    // See http://hyperelliptic.org/EFD/g1p/auto-code/shortw/jacobian-0/doubling/dbl-2009-l.op3
    val a  = x * x
    val b  = y * y
    val c  = b * b
    val d  = ((x + b) * (x + b) - a - c) * 2
    val e  = a * 3
    val rx = e * e - (d * 2)
    val ry = e * (d - rx) - (c * 8)
    val rz = y * z * 2

    TwistPoint(rx, ry, rz, t)
  }

  def infinity: TwistPoint = this.copy(z = Fp2.Zero)

  def *(scalar: BigInt): TwistPoint =
    (0 until scalar.bitLength).toList.foldRight(infinity) { (i, r) =>
      val tt = r.double()
      if (scalar.testBit(i)) tt + this else tt
    }

  def makeAffine(): TwistPoint =
    if (z.isOne) {
      this
    } else if (isInfinity) {
      TwistPoint(Fp2.Zero, Fp2.One, Fp2.Zero, Fp2.Zero)
    } else {
      val zInv  = z.invert()
      val zInv2 = zInv.square()
      TwistPoint(x * zInv2, y * zInv * zInv2, Fp2.One, Fp2.One)
    }

  def unary_-(): TwistPoint =
    TwistPoint(x, -y, z, Fp2.Zero)
}
