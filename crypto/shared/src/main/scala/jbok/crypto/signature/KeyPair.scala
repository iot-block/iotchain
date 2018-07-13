package jbok.crypto.signature

import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}

import scodec.bits.ByteVector

case class KeyPair(public: KeyPair.Public, secret: KeyPair.Secret)

object KeyPair {

  def fromJavaKeyPair(keyPair: java.security.KeyPair): KeyPair = {
    KeyPair(Public(keyPair.getPublic.getEncoded), Secret(keyPair.getPrivate.getEncoded))
  }

  case class Public(value: ByteVector) extends AnyVal {
    def bytes: Array[Byte] = value.toArray
    def encoded: X509EncodedKeySpec = new X509EncodedKeySpec(bytes)
    def toHex: String = value.toHex
  }

  object Public {
    def apply(bytes: Array[Byte]): Public = Public(ByteVector(bytes))
    def apply(hex: String): Public = Public(ByteVector.fromValidHex(hex))
  }

  case class Secret(value: ByteVector) extends AnyVal {
    def bytes: Array[Byte] = value.toArray
    def encoded: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(bytes)
    def toHex: String = value.toHex
  }

  object Secret {
    def apply(bytes: Array[Byte]): Secret = Secret(ByteVector(bytes))
    def apply(hex: String): Secret = Secret(ByteVector.fromValidHex(hex))
  }
}