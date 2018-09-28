package jbok.crypto.signature

import java.math.BigInteger
import java.util.Random

import cats.effect.IO
import jbok.crypto.facade.{BN, EC, SignatureEC}

import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.Uint8Array

trait SignaturePlatform {
  val ecdsaPlatform: Signature[IO, ECDSA] = new ECDSAPlatform
}

class ECDSAPlatform extends Signature[IO, ECDSA] {
  val secp256k1 = new EC("secp256k1")

  override def generateKeyPair(random: Option[Random]): IO[KeyPair] = IO {
    val keyPair = secp256k1.genKeyPair()
    val secret = KeyPair.Secret(keyPair.getPrivate("hex"))
    // drop uncompressed indicator, make it 64-bytes
    val pubkey = KeyPair.Public(keyPair.getPublic(false, "hex").drop(2))
    KeyPair(pubkey, secret)
  }

  override def generatePublicKey(secret: KeyPair.Secret): IO[KeyPair.Public] = IO {
    val keyPair = secp256k1.keyFromPrivate(secret.bytes.toHex, "hex")
    // drop uncompressed indicator, make it 64-bytes
    KeyPair.Public(keyPair.getPublic(false, "hex").drop(2))
  }

  override def sign(hash: Array[Byte], keyPair: KeyPair, chainId: Option[Byte]): IO[CryptoSignature] = IO {
    val kp = secp256k1.keyFromPrivate(keyPair.secret.bytes.toHex, "hex")
    val sig = secp256k1.sign(new Uint8Array(hash.toJSArray), kp)
    val r      = new BigInteger(sig.r.toString)
    val s      = new BigInteger(sig.s.toString)
    CryptoSignature(r, s, (sig.recoveryParam + 27).toByte)
  }

  override def verify(hash: Array[Byte], sig: CryptoSignature, public: KeyPair.Public): IO[Boolean] = IO {
    val signatureEC = convert(sig)
    val key         = secp256k1.keyFromPublic("04" + public.bytes.toHex, "hex")
    secp256k1.verify(new Uint8Array(hash.toJSArray), signatureEC, key)
  }

  override def recoverPublic(hash: Array[Byte], sig: CryptoSignature, chainId: Option[Byte]): Option[KeyPair.Public] = {
    val signatureEC = convert(sig)
    val msg = new Uint8Array(hash.toJSArray)
    val recId = secp256k1.getKeyRecoveryParam(msg, signatureEC)
    val point = secp256k1.recoverPubKey(new Uint8Array(hash.toJSArray), signatureEC, recId)
    Some(KeyPair.Public(point.encode("hex", false).drop(2)))
  }

  def convert(sig: CryptoSignature) = {
    val r           = new BN(sig.r.toString(16), 16)
    val s           = new BN(sig.s.toString(16), 16)
    SignatureEC(r, s, recoveryParam = sig.v.toInt - 27)
  }
}
