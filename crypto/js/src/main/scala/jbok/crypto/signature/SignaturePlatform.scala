package jbok.crypto.signature

import java.math.BigInteger
import java.util.Random

import cats.effect.Sync
import jbok.crypto.facade.{BN, EC, SignatureEC}

import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.Uint8Array

trait SignaturePlatform {
  val ecdsa: Signature[ECDSA] = ECDSAPlatform
}

private object ECDSAPlatform extends Signature[ECDSA] {
  import ECDSACommon._
  val secp256k1 = new EC("secp256k1")

  override def generateKeyPair[F[_]](random: Option[Random])(implicit F: Sync[F]): F[KeyPair] = F.delay {
    val keyPair = secp256k1.genKeyPair()
    val secret  = KeyPair.Secret(keyPair.getPrivate("hex"))
    // drop uncompressed indicator, make it 64-bytes
    val pubkey = KeyPair.Public(keyPair.getPublic(false, "hex").drop(2))
    KeyPair(pubkey, secret)
  }

  override def generatePublicKey[F[_]](secret: KeyPair.Secret)(implicit F: Sync[F]): F[KeyPair.Public] = F.delay {
    val keyPair = secp256k1.keyFromPrivate(secret.bytes.toHex, "hex")
    // drop uncompressed indicator, make it 64-bytes
    KeyPair.Public(keyPair.getPublic(false, "hex").drop(2))
  }

  override def sign[F[_]](hash: Array[Byte], keyPair: KeyPair, chainId: BigInt)(
      implicit F: Sync[F]): F[CryptoSignature] = F.delay {
    val kp  = secp256k1.keyFromPrivate(keyPair.secret.bytes.toHex, "hex")
    val sig = secp256k1.sign(new Uint8Array(hash.toJSArray), kp)
    val r   = new BigInteger(sig.r.toString)
    val s   = new BigInteger(sig.s.toString)
    val pointSign = calculatePointSign(r, toCanonicalS(s), keyPair, hash, chainId) match {
      case Some(recId) => recId
      case None        => throw new Exception("unexpected error")
    }
    val rid: BigInt = getRecoveryId(chainId, pointSign).getOrElse(pointSign)
    CryptoSignature(r, toCanonicalS(s), rid)
  }

  override def verify[F[_]](hash: Array[Byte], sig: CryptoSignature, public: KeyPair.Public, chainId: BigInt)(
      implicit F: Sync[F]): F[Boolean] = F.delay {
    val v: Option[BigInt] = getPointSign(chainId, sig.v)
    v.exists { bigInt =>
      val signatureEC = convert(sig.copy(v = bigInt))
      val key         = secp256k1.keyFromPublic(UNCOMPRESSED_INDICATOR_STRING + public.bytes.toHex, "hex")
      secp256k1.verify(new Uint8Array(hash.toJSArray), signatureEC, key)
    }
  }

  override def recoverPublic(hash: Array[Byte], sig: CryptoSignature, chainId: BigInt): Option[KeyPair.Public] = {
    val v: Option[BigInt] = getPointSign(chainId, sig.v)
    v.map { bigInt =>
      val signatureEC = convert(sig.copy(v = bigInt))
      val msg         = new Uint8Array(hash.toJSArray)
      val recId       = secp256k1.getKeyRecoveryParam(msg, signatureEC)
      val point       = secp256k1.recoverPubKey(new Uint8Array(hash.toJSArray), signatureEC, recId)
      KeyPair.Public(point.encode("hex", false).drop(2))
    }
  }

  private def convert(sig: CryptoSignature) = {
    val r = new BN(sig.r.toString(16), 16)
    val s = new BN(sig.s.toString(16), 16)
    SignatureEC(r, s, recoveryParam = (sig.v - NEGATIVE_POINT_SIGN).toInt)
  }

  private def calculatePointSign(r: BigInt,
                                 s: BigInt,
                                 keyPair: KeyPair,
                                 hash: Array[Byte],
                                 chainId: BigInt): Option[BigInt] =
    allowedPointSigns.find(
      v =>
        recoverPublic(hash, CryptoSignature(r, s, getRecoveryId(chainId, v).getOrElse(v)), chainId)
          .contains(keyPair.public))
}
