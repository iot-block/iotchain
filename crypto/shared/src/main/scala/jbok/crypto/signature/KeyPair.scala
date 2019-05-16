package jbok.crypto.signature

import java.math.BigInteger

import cats.effect.IO
import jbok.codec.json.implicits._
import io.circe.generic.JsonCodec
import scodec.bits.ByteVector

@JsonCodec
final case class KeyPair(public: KeyPair.Public, secret: KeyPair.Secret)

object KeyPair {
  @JsonCodec
  final case class Public(bytes: ByteVector) extends AnyVal

  object Public {
    def apply(hex: String): Public        = Public(ByteVector.fromValidHex(hex))
    def apply(bytes: Array[Byte]): Public = Public(ByteVector(bytes))
  }

  @JsonCodec
  final case class Secret(bytes: ByteVector) extends AnyVal {
    def d: BigInteger = new BigInteger(1, bytes.toArray)
  }

  object Secret {
    def apply(d: BigInteger): Secret      = apply(ByteVector(d.toByteArray))
    def apply(hex: String): Secret        = apply(ByteVector.fromValidHex(hex))
    def apply(bytes: Array[Byte]): Secret = apply(ByteVector(bytes))
    def apply(bv: ByteVector): Secret     = new Secret(bv.takeRight(32).padLeft(32))
  }

  def fromSecret(secret: ByteVector): KeyPair = {
    val sec = KeyPair.Secret(secret)
    val pub = Signature[ECDSA].generatePublicKey[IO](sec).unsafeRunSync()
    KeyPair(pub, sec)
  }
}
