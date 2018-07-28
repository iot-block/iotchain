package jbok.crypto.signature

import java.math.BigInteger

import scodec.bits.ByteVector

case class KeyPair(public: KeyPair.Public, secret: KeyPair.Secret)

object KeyPair {
  case class Public(bytes: ByteVector) extends AnyVal {
    def uncompressed: ByteVector = bytes.tail
  }

  object Public {
    def apply(hex: String): Public = Public(ByteVector.fromValidHex(hex))
    def apply(bytes: Array[Byte]): Public = Public(ByteVector(bytes))
  }

  case class Secret(bytes: ByteVector) extends AnyVal {
    def d: BigInt = BigInt(bytes.toArray)
  }

  object Secret {
    def apply(d: BigInteger): Secret = Secret(ByteVector(d.toByteArray))
    def apply(d: BigInt): Secret = Secret(ByteVector(d.toByteArray))
    def apply(hex: String): Secret = Secret(ByteVector.fromValidHex(hex))
    def apply(bytes: Array[Byte]): Secret = Secret(ByteVector(bytes))
  }
}
