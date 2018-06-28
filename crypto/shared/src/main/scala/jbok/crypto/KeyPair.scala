package jbok.crypto

import scodec.bits._
case class KeyPair(publicKey: KeyPair.Public, secretKey: KeyPair.Secret)

object KeyPair {

  case class Public(value: ByteVector) extends AnyVal {
    def bytes: Array[Byte] = value.toArray
  }

  case class Secret(value: ByteVector) extends AnyVal {
    def bytes: Array[Byte] = value.toArray
  }

  def fromBytes(pk: Array[Byte], sk: Array[Byte]): KeyPair = fromByteVectors(ByteVector(pk), ByteVector(sk))
  def fromByteVectors(pk: ByteVector, sk: ByteVector): KeyPair = KeyPair(Public(pk), Secret(sk))
}
