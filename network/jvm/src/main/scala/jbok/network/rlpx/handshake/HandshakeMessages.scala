package jbok.network.rlpx.handshake

import jbok.crypto.signature.{KeyPair, CryptoSignature}
import jbok.network.rlpx.handshake.AuthInitiateEcdsaCodec._
import scodec.bits.ByteVector

case class AuthInitiateMessage(
                                signature: CryptoSignature,
                                ephemeralPublicHash: ByteVector,
                                publicKey: ByteVector,
                                nonce: ByteVector,
                                knownPeer: Boolean
)

object AuthInitiateMessage {
  val NonceLength         = 32
  val EphemeralHashLength = 32
  val PublicKeyLength     = 64
  val KnownPeerLength     = 1
  val EncodedLength: Int  = 32 + 32 + 1 + EphemeralHashLength + PublicKeyLength + NonceLength + KnownPeerLength

  def decode(input: Array[Byte]): AuthInitiateMessage = {
    val publicKeyIndex = EncodedLength + EphemeralHashLength
    val nonceIndex     = publicKeyIndex + PublicKeyLength
    val knownPeerIndex = nonceIndex + NonceLength

    AuthInitiateMessage(
      signature = decodeECDSA(input.take(32 + 32 + 1)),
      ephemeralPublicHash = ByteVector(input.slice(32 + 32 + 1, publicKeyIndex)),
      publicKey = ByteVector(0x04.toByte +: input.slice(publicKeyIndex, nonceIndex)),
      nonce = ByteVector(input.slice(nonceIndex, knownPeerIndex)),
      knownPeer = input(knownPeerIndex) == 1
    )
  }
}

case class AuthInitiateMessageV4(signature: CryptoSignature, publicKey: ByteVector, nonce: ByteVector, version: Int)

case class AuthResponseMessage(ephemeralPublicKey: KeyPair.Public, nonce: ByteVector, knownPeer: Boolean) {
  lazy val encoded: ByteVector = ByteVector(
    ephemeralPublicKey.bytes.toArray ++ nonce.toArray ++ Array(if (knownPeer) 1.toByte else 0.toByte)
  )
}

object AuthResponseMessage {
  private val PublicKeyLength = 64
  private val NonceLength     = 32
  private val KnownPeerLength = 1

  val EncodedLength: Int = PublicKeyLength + NonceLength + KnownPeerLength

  def decode(input: Array[Byte]): AuthResponseMessage =
    AuthResponseMessage(
      ephemeralPublicKey = KeyPair.Public(input.take(PublicKeyLength)),
      nonce = ByteVector(input.slice(PublicKeyLength, PublicKeyLength + NonceLength)),
      knownPeer = input(PublicKeyLength + NonceLength) == 1
    )
}

case class AuthResponseMessageV4(ephemeralPublicKey: KeyPair.Public, nonce: ByteVector, version: Int)
