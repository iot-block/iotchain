package jbok.crypto.signature

import java.math.BigInteger

import scodec.bits.ByteVector

case class KeyPair(public: KeyPair.Public, secret: KeyPair.Secret)

object KeyPair {
  case class Public(bytes: ByteVector) extends AnyVal

  object Public {
    def apply(hex: String): Public        = Public(ByteVector.fromValidHex(hex))
    def apply(bytes: Array[Byte]): Public = Public(ByteVector(bytes))
  }

  case class Secret(bytes: ByteVector) extends AnyVal {
    def d: BigInteger = new BigInteger(bytes.toArray)
  }

  object Secret {
    def apply(d: BigInteger): Secret      = Secret(ByteVector(d.toByteArray))
    def apply(hex: String): Secret        = Secret(ByteVector.fromValidHex(hex))
    def apply(bytes: Array[Byte]): Secret = Secret(ByteVector(bytes))
  }
}
