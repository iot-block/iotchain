package jbok.common

import spire.math.SafeLong

package object math {
  type N = SafeLong

  object implicits extends N.implicits

  object N {
    final val zero: N = SafeLong.zero

    final val one: N = SafeLong.one

    def apply(i: Int): N = SafeLong(i.toLong)

    def apply(l: Long): N = SafeLong(l)

    def apply(b: BigInt): N = SafeLong(b)

    def apply(s: String): N = N(BigInt(s))

    def apply(byteArray: Array[Byte]): N = N(BigInt(1, byteArray))

    trait implicits {
      implicit def int2N(i: Int): N = N(i)

      implicit def long2N(l: Long): N = N(l)

      implicit def bigInt2N(bigInt: BigInt): N = N(bigInt)

      implicit def numberSyntax(n: N): JbokMathNOps = new JbokMathNOps(n)
    }

    final class JbokMathNOps(val n: N) extends AnyVal {
      def toByteArray: Array[Byte] = n.toBigInt.toByteArray
    }
  }
}
