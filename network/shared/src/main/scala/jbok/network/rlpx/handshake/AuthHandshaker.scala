package jbok.network.rlpx.handshake

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.SecureRandom

import cats.effect.{IO, Sync}
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.crypto.signature.SecP256k1._
import jbok.crypto.signature.{CryptoSignature, KeyPair, SecP256k1}
import jbok.crypto.{ECIES, _}
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.digests.KeccakDigest
import scodec.bits.{BitVector, ByteVector}
import jbok.codec.rlp.codecs._

import scala.util.Random

sealed trait AuthHandshakeResult
case object AuthHandshakeError extends AuthHandshakeResult
case class AuthHandshakeSuccess(secrets: Secrets, remotePubKey: ByteVector) extends AuthHandshakeResult

class Secrets(
    val aes: Array[Byte],
    val mac: Array[Byte],
    val token: Array[Byte],
    val egressMac: KeccakDigest,
    val ingressMac: KeccakDigest
)

/**
  * New: authInitiator -> E(remote-pubk, S(ephemeral-privk, static-shared-secret ^^ nonce) || H(ephemeral-pubk) || pubk || nonce || 0x0)
  * authRecipient -> E(remote-pubk, remote-ephemeral-pubk || nonce || 0x0)
  *
  * Known: authInitiator = E(remote-pubk, S(ephemeral-privk, token ^^ nonce) || H(ephemeral-pubk) || pubk || nonce || 0x1)
  * authRecipient = E(remote-pubk, remote-ephemeral-pubk || nonce || 0x1) // token found
  * authRecipient = E(remote-pubk, remote-ephemeral-pubk || nonce || 0x0) // token not found
  *
  * static-shared-secret = ecdh.agree(privkey, remote-pubk)
  * ephemeral-shared-secret = ecdh.agree(ephemeral-privk, remote-ephemeral-pubk)
  *
  * ephemeral-shared-secret = ecdh.agree(ephemeral-privkey, remote-ephemeral-pubk)
  * shared-secret = sha3(ephemeral-shared-secret || sha3(nonce || initiator-nonce))
  * token = sha3(shared-secret)
  * aes-secret = sha3(ephemeral-shared-secret || shared-secret)
  * # destroy shared-secret
  * mac-secret = sha3(ephemeral-shared-secret || aes-secret)
  * # destroy ephemeral-shared-secret
  *
  * Initiator:
  * egress-mac = sha3.update(mac-secret ^ recipient-nonce || auth-sent-init)
  * # destroy nonce
  * ingress-mac = sha3.update(mac-secret ^ initiator-nonce || auth-recvd-ack)
  * # destroy remote-nonce
  *
  * Recipient:
  * egress-mac = sha3.update(mac-secret ^ initiator-nonce || auth-sent-ack)
  * # destroy nonce
  * ingress-mac = sha3.update(mac-secret ^ recipient-nonce || auth-recvd-init)
  * # destroy remote-nonce
  *
  */
case class AuthHandshaker[F[_]](
    nodeKey: KeyPair,
    nonce: ByteVector,
    ephemeralKey: KeyPair,
    secureRandom: SecureRandom,
    isInitiator: Boolean = false,
    initiatePacketOpt: Option[ByteVector] = None,
    responsePacketOpt: Option[ByteVector] = None,
    remotePubKeyOpt: Option[ByteVector] = None
)(implicit F: Sync[F]) {
  import AuthHandshaker._
  def initiate(nodeId: String): F[(ByteVector, AuthHandshaker[F])] = {
    val remotePubKey = publicKeyFromNodeId(nodeId)
    val message = createAuthInitiateMessageV4(remotePubKey)
    val encoded: Array[Byte] = RlpCodec.encode(message).require.bytes.toArray
    val padded = encoded ++ randomBytes(Random.nextInt(MaxPadding - MinPadding) + MinPadding)
    val encryptedSize = padded.length + ECIES.OverheadSize
    val sizePrefix = ByteBuffer.allocate(2).putShort(encryptedSize.toShort).array

    for {
      encryptedPayload <- ECIES.encrypt[F](
        SecP256k1.toECPublicKeyParameters(KeyPair.Public(remotePubKey)).getQ,
        secureRandom,
        padded,
        Some(sizePrefix)
      )
    } yield {
      val packet = ByteVector(sizePrefix) ++ encryptedPayload
      (packet, copy(isInitiator = true, initiatePacketOpt = Some(packet), remotePubKeyOpt = Some(remotePubKey)))
    }
  }

  def handleResponseMessage(data: ByteVector): F[(AuthHandshakeResult, ByteVector)] =
    for {
      plaintext <- ECIES.decrypt[F](nodeKey.secret.d.underlying(), data.toArray)
      message = AuthResponseMessage.decode(plaintext.toArray)
    } yield {
      val result = copy(responsePacketOpt = Some(data.take(ResponsePacketLength))).finalizeHandshake(message.ephemeralPublicKey, message.nonce)
      result -> data.drop(ResponsePacketLength)
    }

  def handleResponseMessageV4(data: ByteVector): F[(AuthHandshakeResult, ByteVector)] = {
    val (initData, remaining) = decodeV4Packet(data)
    val sizeBytes = initData.take(2)
    val encryptedPayload = initData.drop(2)

    for {
      plaintext <- ECIES.decrypt[F](
        privKey = nodeKey.secret.d.underlying(),
        ciphertext = encryptedPayload.toArray,
        macData = Some(sizeBytes.toArray)
      )
      message = RlpCodec.decode[AuthResponseMessageV4](BitVector(plaintext)).require.value
    } yield {
      val result = copy(responsePacketOpt = Some(initData)).finalizeHandshake(message.ephemeralPublicKey, message.nonce)
      result -> remaining
    }
  }

  def handleResponseMessageAll(data: ByteVector): F[(AuthHandshakeResult, ByteVector)] = {
    println(s"received response data ${data}")
    handleResponseMessage(data).attemptT.getOrElseF(handleResponseMessageV4(data))
  }

  private def decodeV4Packet(data: ByteVector): (ByteVector, ByteVector) = {
    val encryptedPayloadSize = bigEndianToShort(data.take(2).toArray)
    val (packetData, remainingData) = data.splitAt(encryptedPayloadSize + 2)
    packetData -> remainingData
  }

  private def bigEndianToShort(bs: Array[Byte]): Short = {
    val n = bs(0) << 8
    (n | bs(1) & 0xFF).toShort
  }

  def handleInitialMessageAll(data: ByteVector): F[(ByteVector, AuthHandshakeResult, ByteVector)] = {
    println(s"received init data ${data}")
    handleInitialMessage(data).attemptT.getOrElseF(handleInitialMessageV4(data))
  }

  def handleInitialMessage(data: ByteVector): F[(ByteVector, AuthHandshakeResult, ByteVector)] = {
    val initData = data.take(InitiatePacketLength)
    for {
      plaintext <- ECIES.decrypt[F](nodeKey.secret.d.underlying(), initData.toArray)
      message = AuthInitiateMessage.decode(plaintext.toArray)
      response = AuthResponseMessage(
        ephemeralPublicKey = ephemeralKey.public.bytes,
        nonce = nonce,
        knownPeer = false
      )

      encryptedPacket <- ECIES.encrypt[F](
        SecP256k1.toECPublicKeyParameters(KeyPair.Public(message.publicKey)).getQ,
        secureRandom,
        response.encoded.toArray,
        None
      )

    } yield {
      val remoteEphemeralKey = extractEphemeralKey(message.signature, message.nonce, message.publicKey)
      val handshakeResult =
        copy(initiatePacketOpt = Some(initData),
          responsePacketOpt = Some(encryptedPacket),
          remotePubKeyOpt = Some(message.publicKey)).finalizeHandshake(remoteEphemeralKey, message.nonce)

      (encryptedPacket, handshakeResult, data.drop(InitiatePacketLength))
    }
  }

  def handleInitialMessageV4(data: ByteVector): F[(ByteVector, AuthHandshakeResult, ByteVector)] = {
    val (initData, remaining) = decodeV4Packet(data)
    val sizeBytes = initData.take(2)
    val encryptedPayload = initData.drop(2)

    for {
      plaintext <- ECIES.decrypt[F](
        privKey = nodeKey.secret.d.underlying(),
        ciphertext = encryptedPayload.toArray,
        macData = Some(sizeBytes.toArray)
      )
      message = RlpCodec.decode[AuthInitiateMessageV4](BitVector(plaintext)).require.value
      response = AuthResponseMessageV4(
        ephemeralPublicKey = ephemeralKey.public.bytes,
        nonce = nonce,
        version = ProtocolVersion
      )
      encodedResponse = RlpCodec.encode(response).require.toByteArray

      encryptedSize = encodedResponse.length + ECIES.OverheadSize
      sizePrefix = ByteBuffer.allocate(2).putShort(encryptedSize.toShort).array
      encryptedResponsePayload <- ECIES.encrypt[F](
        SecP256k1.toECPublicKeyParameters(KeyPair.Public(message.publicKey)).getQ,
        secureRandom,
        encodedResponse,
        Some(sizePrefix)
      )
    } yield {
      val packet = ByteVector(sizePrefix) ++ encryptedResponsePayload

      val remoteEphemeralKey = extractEphemeralKey(message.signature, message.nonce, message.publicKey)

      val handshakeResult = copy(initiatePacketOpt = Some(initData),
                             responsePacketOpt = Some(packet),
                             remotePubKeyOpt = Some(message.publicKey))
        .finalizeHandshake(remoteEphemeralKey, message.nonce)
      (packet, handshakeResult, remaining)
    }
  }

  private def extractEphemeralKey(signature: CryptoSignature, nonce: ByteVector, publicKey: ByteVector): ByteVector = {
    val agreement = new ECDHBasicAgreement
    agreement.init(SecP256k1.toECPrivateKeyParameters(nodeKey.secret))
    val sharedSecret = agreement.calculateAgreement(SecP256k1.toECPublicKeyParameters(KeyPair.Public(publicKey)))

    val token = bigIntegerToBytes(sharedSecret, NonceSize)
    val signed = xor(token, nonce.toArray)

    val signaturePubBytes = SecP256k1
      .recoverPublicBytes(
        signature.r,
        signature.s,
        signature.v.get,
        None,
        ByteVector(signed)
      )
      .get
      .toArray

    ByteVector(uncompressedIndicator +: signaturePubBytes)
  }

  private def xor(a: Array[Byte], b: Array[Byte]): Array[Byte] =
    (a zip b) map { case (b1, b2) => (b1 ^ b2).toByte }

  private def createAuthInitiateMessageV4(remotePubKey: ByteVector): AuthInitiateMessageV4 = {
    val sharedSecret = {
      val agreement = new ECDHBasicAgreement
      agreement.init(SecP256k1.toECPrivateKeyParameters(nodeKey.secret))
      bigIntegerToBytes(agreement.calculateAgreement(SecP256k1.toECPublicKeyParameters(KeyPair.Public(remotePubKey))),
                        NonceSize)
    }

    val messageToSign = ByteVector(sharedSecret).xor(nonce)
    val signature = SecP256k1.sign[IO](messageToSign, ephemeralKey).unsafeRunSync()

    AuthInitiateMessageV4(signature, nodeKey.public.bytes, nonce, ProtocolVersion)
  }

  private[jbok] def bigIntegerToBytes(b: BigInteger, numBytes: Int): Array[Byte] = {
    val bytes = new Array[Byte](numBytes)
    val biBytes = b.toByteArray
    val start = if (biBytes.length == numBytes + 1) 1 else 0
    val length = Math.min(biBytes.length, numBytes)
    System.arraycopy(biBytes, start, bytes, numBytes - length, length)
    bytes
  }

  private def finalizeHandshake(remoteEphemeralKey: ByteVector, remoteNonce: ByteVector): AuthHandshakeResult = {
    val successOpt = for {
      initiatePacket <- initiatePacketOpt
      responsePacket <- responsePacketOpt
      remotePubKey <- remotePubKeyOpt
    } yield {
      val secretScalar = {
        val agreement = new ECDHBasicAgreement
        agreement.init(SecP256k1.toECPrivateKeyParameters(ephemeralKey.secret))
        agreement.calculateAgreement(SecP256k1.toECPublicKeyParameters(KeyPair.Public(remoteEphemeralKey)))
      }

      val agreedSecret = bigIntegerToBytes(secretScalar, SecretSize)

      val sharedSecret =
        if (isInitiator) (agreedSecret ++ (remoteNonce.toArray ++ nonce.toArray).kec256).kec256
        else (agreedSecret ++ (nonce.toArray ++ remoteNonce.toArray).kec256).kec256

      val aesSecret = (agreedSecret ++ sharedSecret).kec256

      val (egressMacSecret, ingressMacSecret) =
        if (isInitiator) macSecretSetup(agreedSecret, aesSecret, initiatePacket, nonce, responsePacket, remoteNonce)
        else macSecretSetup(agreedSecret, aesSecret, initiatePacket, remoteNonce, responsePacket, nonce)

      AuthHandshakeSuccess(
        secrets = new Secrets(aes = aesSecret,
                              mac = (agreedSecret ++ aesSecret).kec256,
                              token = sharedSecret.kec256,
                              egressMac = egressMacSecret,
                              ingressMac = ingressMacSecret),
        remotePubKey = remotePubKey
      )
    }

    successOpt getOrElse AuthHandshakeError
  }

  private def macSecretSetup(
      agreedSecret: Array[Byte],
      aesSecret: Array[Byte],
      initiatePacket: ByteVector,
      initiateNonce: ByteVector,
      responsePacket: ByteVector,
      responseNonce: ByteVector
  ) = {
    val macSecret = (agreedSecret ++ aesSecret).kec256

    val mac1 = new KeccakDigest(MacSize)
    mac1.update(xor(macSecret, responseNonce.toArray), 0, macSecret.length)
    val bufSize = 32
    val buf = new Array[Byte](bufSize)
    new KeccakDigest(mac1).doFinal(buf, 0)
    mac1.update(initiatePacket.toArray, 0, initiatePacket.toArray.length)
    new KeccakDigest(mac1).doFinal(buf, 0)

    val mac2 = new KeccakDigest(MacSize)
    mac2.update(xor(macSecret, initiateNonce.toArray), 0, macSecret.length)
    new KeccakDigest(mac2).doFinal(buf, 0)
    mac2.update(responsePacket.toArray, 0, responsePacket.toArray.length)
    new KeccakDigest(mac2).doFinal(buf, 0)

    if (isInitiator) (mac1, mac2)
    else (mac2, mac1)
  }

  private[jbok] def publicKeyFromNodeId(nodeId: String): ByteVector = {
    val bytes = uncompressedIndicator +: ByteVector.fromValidHex(nodeId)
    bytes
  }
}

object AuthHandshaker {
  val InitiatePacketLength = AuthInitiateMessage.EncodedLength + ECIES.OverheadSize
  val ResponsePacketLength = AuthResponseMessage.EncodedLength + ECIES.OverheadSize

  val NonceSize = 32
  val MacSize = 256
  val SecretSize = 32
  val MinPadding = 100
  val MaxPadding = 300
  val ProtocolVersion = 4

  def randomBytes(len: Int): Array[Byte] = {
    val arr = new Array[Byte](len)
    new Random().nextBytes(arr)
    arr
  }

  def apply[F[_]: Sync](nodeKey: KeyPair, secureRandom: SecureRandom): AuthHandshaker[F] = {
    val nonce = secureRandomByteArray(secureRandom, NonceSize)
    AuthHandshaker(nodeKey, ByteVector(nonce), SecP256k1.generateKeyPair[IO].unsafeRunSync(), secureRandom)
  }

}
