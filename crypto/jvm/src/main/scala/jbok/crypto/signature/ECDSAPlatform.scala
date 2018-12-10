package jbok.crypto.signature

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Random

import cats.effect.Sync
import cats.implicits._
import org.bouncycastle.asn1.x9.X9IntegerConverter
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.{
  ECDomainParameters,
  ECKeyGenerationParameters,
  ECPrivateKeyParameters,
  ECPublicKeyParameters
}
import org.bouncycastle.crypto.signers.{ECDSASigner, HMacDSAKCalculator}
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve

object ECDSAPlatform extends Signature[ECDSA] {

  import ECDSAChainIdConvert._

  val curve: ECNamedCurveParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
  val domain: ECDomainParameters       = new ECDomainParameters(curve.getCurve, curve.getG, curve.getN, curve.getH)
  val halfCurveOrder                   = curve.getN.shiftRight(1)

  override def generateKeyPair[F[_]](random: Option[Random])(implicit F: Sync[F]): F[KeyPair] = {
    val secureRandom = new SecureRandom()
    val generator    = new ECKeyPairGenerator()
    val keygenParams = new ECKeyGenerationParameters(domain, secureRandom)
    generator.init(keygenParams)
    for {
      keyPair <- F.delay(generator.generateKeyPair())
      privParams = keyPair.getPrivate.asInstanceOf[ECPrivateKeyParameters]
      secret     = KeyPair.Secret(privParams.getD)
      public <- generatePublicKey[F](secret)
    } yield KeyPair(public, secret)
  }

  override def generatePublicKey[F[_]](secret: KeyPair.Secret)(implicit F: Sync[F]): F[KeyPair.Public] = F.delay {
    val q = curve.getG.multiply(secret.d).getEncoded(false).tail
    require(q.length == 64, s"public key length should be 64 instead of ${q.length}")
    KeyPair.Public(q)
  }

  override def sign[F[_]](hash: Array[Byte], keyPair: KeyPair, chainId: BigInt)(
      implicit F: Sync[F]): F[CryptoSignature] = F.delay {
    val signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()))
    signer.init(true, new ECPrivateKeyParameters(keyPair.secret.d, domain))
    val Array(r, s) = signer.generateSignature(hash)
    val v           = calculateRecId(r, toCanonicalS(s), keyPair, hash, chainId).get

    val pointSign: BigInt = getRecoveryId(chainId, v).getOrElse(v)

    CryptoSignature(r, toCanonicalS(s), pointSign)
  }

  override def verify[F[_]](hash: Array[Byte], sig: CryptoSignature, public: KeyPair.Public, chainId: BigInt)(
      implicit F: Sync[F]): F[Boolean] = F.delay {
    val signer = new ECDSASigner()
    val q      = curve.getCurve.decodePoint(UNCOMPRESSED_INDICATOR_BYTE +: public.bytes.toArray)
    signer.init(false, new ECPublicKeyParameters(q, domain))
    signer.verifySignature(hash, sig.r.bigInteger, sig.s.bigInteger)
  }

  override def recoverPublic(hash: Array[Byte], sig: CryptoSignature, chainId: BigInt): Option[KeyPair.Public] = {
    val order = curve.getN
    val prime = curve.getCurve.asInstanceOf[SecP256K1Curve].getQ

    val bytesOpt = getPointSign(chainId, sig.v).flatMap { recovery =>
      if (sig.r.compareTo(prime) < 0) {
        val R = constructPoint(sig.r, recovery.toInt)
        if (R.multiply(order).isInfinity) {
          val e    = new BigInteger(1, hash)
          val rInv = sig.r.modInverse(order)
          //Q = r^(-1)(sR - eG)
          val q: ECPoint =
            R.multiply(sig.s.bigInteger).subtract(curve.getG.multiply(e)).multiply(rInv.bigInteger)
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
    new ECPublicKeyParameters(curve.getCurve.decodePoint(UNCOMPRESSED_INDICATOR_BYTE +: public.bytes.toArray), domain)

  private[jbok] def toCanonicalS(s: BigInteger): BigInt =
    BigInt(if (s.compareTo(halfCurveOrder) <= 0) {
      s
    } else {
      curve.getN.subtract(s)
    })

  private[jbok] def calculateRecId(r: BigInt,
                                   s: BigInt,
                                   keyPair: KeyPair,
                                   hash: Array[Byte],
                                   chainId: BigInt): Option[BigInt] =
    allowedPointSigns.find(
      v =>
        recoverPublic(hash, CryptoSignature(r, s, getRecoveryId(chainId, v).getOrElse(v)), chainId)
          .contains(keyPair.public))

  private[jbok] def constructPoint(xCoordinate: BigInt, recId: Int): ECPoint = {
    val x9      = new X9IntegerConverter
    val compEnc = x9.integerToBytes(xCoordinate.bigInteger, 1 + x9.getByteLength(curve.getCurve))
    compEnc(0) = if (recId == POSITIVE_POINT_SIGN) 3.toByte else 2.toByte
    curve.getCurve.decodePoint(compEnc)
  }
}
