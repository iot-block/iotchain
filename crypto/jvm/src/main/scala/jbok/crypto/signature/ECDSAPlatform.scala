package jbok.crypto.signature

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Random

import cats.effect.Sync
import cats.implicits._
import org.bouncycastle.asn1.x9.X9IntegerConverter
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.{ECDomainParameters, ECKeyGenerationParameters, ECPrivateKeyParameters, ECPublicKeyParameters}
import org.bouncycastle.crypto.signers.{ECDSASigner, HMacDSAKCalculator}
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve

class ECDSAPlatform[F[_]](curveName: String)(implicit F: Sync[F]) extends Signature[F, ECDSA] {

  override def toString: String = s"ECDSASignature(${curveName})"

  import ECDSAPlatform._

  val curve: ECNamedCurveParameterSpec = ECNamedCurveTable.getParameterSpec(curveName)
  val domain: ECDomainParameters       = new ECDomainParameters(curve.getCurve, curve.getG, curve.getN, curve.getH)
  val halfCurveOrder                   = curve.getN.shiftRight(1)

  override def generateKeyPair(random: Option[Random]): F[KeyPair] = {
    val secureRandom = new SecureRandom()
    val generator    = new ECKeyPairGenerator()
    val keygenParams = new ECKeyGenerationParameters(domain, secureRandom)
    generator.init(keygenParams)
    for {
      keyPair <- F.delay(generator.generateKeyPair())
      privParams = keyPair.getPrivate.asInstanceOf[ECPrivateKeyParameters]
      secret     = KeyPair.Secret(privParams.getD)
      public <- generatePublicKey(secret)
    } yield KeyPair(public, secret)
  }

  override def generatePublicKey(secret: KeyPair.Secret): F[KeyPair.Public] = F.delay {
    val q = curve.getG.multiply(secret.d).getEncoded(false).tail
    require(q.length == 64, s"public key length should be 64 instead of ${q.length}")
    KeyPair.Public(q)
  }

  override def sign(hash: Array[Byte], keyPair: KeyPair, chainId: Option[Byte]): F[CryptoSignature] = F.delay {
    val signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()))
    signer.init(true, new ECPrivateKeyParameters(keyPair.secret.d, domain))
    val Array(r, s) = signer.generateSignature(hash)
    val v           = calculateRecId(r, toCanonicalS(s), keyPair, hash).get

    val pointSign = chainId match {
      case Some(id) if v == NEGATIVE_POINT_SIGN => (id * 2 + EIP155_NEGATIVE_POINT_SIGN).toByte
      case Some(id) if v == POSITIVE_POINT_SIGN => (id * 2 + EIP155_POSITIVE_POINT_SIGN).toByte
      case None                                 => v
    }

    CryptoSignature(r, toCanonicalS(s), pointSign)
  }

  override def verify(hash: Array[Byte], sig: CryptoSignature, public: KeyPair.Public): F[Boolean] = F.delay {
    val signer = new ECDSASigner()
    val q      = curve.getCurve.decodePoint(UNCOMPRESSED_INDICATOR +: public.bytes.toArray)
    signer.init(false, new ECPublicKeyParameters(q, domain))
    signer.verifySignature(hash, sig.r, sig.s)
  }

  override def recoverPublic(hash: Array[Byte], sig: CryptoSignature, chainId: Option[Byte]): Option[KeyPair.Public] = {
    val order = curve.getN
    val prime = curve.getCurve.asInstanceOf[SecP256K1Curve].getQ

    val bytesOpt = getRecoveredPointSign(sig.v, chainId).flatMap { recovery =>
      if (sig.r.compareTo(prime) < 0) {
        val R = constructPoint(sig.r, recovery)
        if (R.multiply(order).isInfinity) {
          val e    = new BigInteger(1, hash)
          val rInv = sig.r.modInverse(order)
          //Q = r^(-1)(sR - eG)
          val q: ECPoint =
            R.multiply(sig.s).subtract(curve.getG.multiply(e)).multiply(rInv)
          Some(q.getEncoded(false).tail)
        } else {
          None
        }
      } else {
        None
      }
    }

    bytesOpt.map(bytes => {
      require(bytes.length == 64)
      KeyPair.Public(bytes)
    })
  }

  private[jbok] def toECPrivateKeyParameters(secret: KeyPair.Secret) =
    new ECPrivateKeyParameters(secret.d, domain)

  private[jbok] def toECPublicKeyParameters(public: KeyPair.Public) =
    new ECPublicKeyParameters(curve.getCurve.decodePoint(UNCOMPRESSED_INDICATOR +: public.bytes.toArray), domain)

  private[jbok] def toCanonicalS(s: BigInteger): BigInteger =
    if (s.compareTo(halfCurveOrder) <= 0) {
      s
    } else {
      curve.getN.subtract(s)
    }

  private[jbok] def calculateRecId(r: BigInteger, s: BigInteger, keyPair: KeyPair, hash: Array[Byte]): Option[Byte] =
    allowedPointSigns.find(v => recoverPublic(hash, CryptoSignature(r, s, v), None).contains(keyPair.public))

  private[jbok] def constructPoint(xCoordinate: BigInt, recId: Int): ECPoint = {
    val x9      = new X9IntegerConverter
    val compEnc = x9.integerToBytes(xCoordinate.bigInteger, 1 + x9.getByteLength(curve.getCurve))
    compEnc(0) = if (recId == POSITIVE_POINT_SIGN) 3.toByte else 2.toByte
    curve.getCurve.decodePoint(compEnc)
  }

  private[jbok] def getRecoveredPointSign(pointSign: Byte, chainId: Option[Byte]): Option[Byte] =
    (chainId match {
      case Some(id) =>
        if (pointSign == NEGATIVE_POINT_SIGN || pointSign == (id * 2 + EIP155_NEGATIVE_POINT_SIGN).toByte) {
          Some(NEGATIVE_POINT_SIGN)
        } else if (pointSign == POSITIVE_POINT_SIGN || pointSign == (id * 2 + EIP155_POSITIVE_POINT_SIGN).toByte) {
          Some(POSITIVE_POINT_SIGN)
        } else {
          None
        }
      case None => Some(pointSign)
    }).filter(pointSign => allowedPointSigns.contains(pointSign))

  private[jbok] def pointSign(chainId: Option[Byte], v: Byte): Byte = chainId match {
    case Some(id) if v == NEGATIVE_POINT_SIGN => (id * 2 + EIP155_NEGATIVE_POINT_SIGN).toByte
    case Some(id) if v == POSITIVE_POINT_SIGN => (id * 2 + EIP155_POSITIVE_POINT_SIGN).toByte
    case None                                 => v
  }
}

object ECDSAPlatform {
  val UNCOMPRESSED_INDICATOR: Byte     = 0x04
  val NEGATIVE_POINT_SIGN: Byte        = 27
  val POSITIVE_POINT_SIGN: Byte        = 28
  val EIP155_NEGATIVE_POINT_SIGN: Byte = 35
  val EIP155_POSITIVE_POINT_SIGN: Byte = 36
  val allowedPointSigns                = Set(NEGATIVE_POINT_SIGN, POSITIVE_POINT_SIGN)
}

