package jbok.crypto.signature
import java.math.BigInteger
import java.security.{SecureRandom, Security}

import cats.effect.{IO, Sync}
import jbok.crypto.signature.SignatureRecover._
import org.bouncycastle.asn1.x9.X9IntegerConverter
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve
import scodec.bits.ByteVector

class ECDSA(curveName: String) extends RecoverableSignature {
  val uncompressedIndicator: Byte = 0x04

  Security.addProvider(new BouncyCastleProvider())

  val curve: ECNamedCurveParameterSpec = ECNamedCurveTable.getParameterSpec(curveName)

  val domain: ECDomainParameters =
    new ECDomainParameters(curve.getCurve, curve.getG, curve.getN, curve.getH)

  val HALF_CURVE_ORDER = curve.getN.shiftRight(1)

  def toCanonicalS(s: BigInteger): BigInteger =
    if (s.compareTo(HALF_CURVE_ORDER) <= 0) {
      s
    } else {
      curve.getN.subtract(s)
    }

  def toAsymmetricCipherKeyPair(keyPair: KeyPair): AsymmetricCipherKeyPair = {
    val secret = new ECPrivateKeyParameters(keyPair.secret.d.underlying(), domain)
    val public = new ECPublicKeyParameters(curve.getCurve.decodePoint(keyPair.public.bytes.toArray), domain)
    new AsymmetricCipherKeyPair(public, secret)
  }

  def toECPrivateKeyParameters(secret: KeyPair.Secret) =
    new ECPrivateKeyParameters(secret.d.underlying(), domain)

  def toECPublicKeyParameters(public: KeyPair.Public) =
    new ECPublicKeyParameters(curve.getCurve.decodePoint(public.bytes.toArray), domain)

  override val algo: String = curveName

  override def buildPublicKeyFromPrivate[F[_]: Sync](secret: KeyPair.Secret): F[KeyPair.Public] = Sync[F].delay {
    val q = curve.getG.multiply(secret.d.underlying()).getEncoded(false)
    KeyPair.Public(q)
  }

  override def generateKeyPair[F[_]](implicit F: Sync[F]): F[KeyPair] = Sync[F].delay {
    val generator = new ECKeyPairGenerator()
    val keygenParams = new ECKeyGenerationParameters(domain, new SecureRandom())
    generator.init(keygenParams)
    val keyPair = generator.generateKeyPair()
    val privParams = keyPair.getPrivate.asInstanceOf[ECPrivateKeyParameters]
    val secret = KeyPair.Secret(privParams.getD)
    val public = buildPublicKeyFromPrivate[IO](secret).unsafeRunSync()
    KeyPair(public, secret)
  }

  override def sign[F[_]](hash: ByteVector, keyPair: KeyPair, chainId: Option[Byte] = None)(
      implicit F: Sync[F]): F[CryptoSignature] =
    Sync[F].delay {
      val signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()))
      signer.init(true, new ECPrivateKeyParameters(keyPair.secret.d.underlying(), domain))
      val Array(r, s) = signer.generateSignature(hash.toArray)
      val v = calculateRecId(r, toCanonicalS(s), keyPair, hash).get

      val pointSign = chainId match {
        case Some(id) if v == negativePointSign => (id * 2 + newNegativePointSign).toByte
        case Some(id) if v == positivePointSign => (id * 2 + newPositivePointSign).toByte
        case None                               => v
      }

      CryptoSignature(r, toCanonicalS(s), Some(pointSign))
    }

  override def verify[F[_]](hash: ByteVector, signed: CryptoSignature, pk: KeyPair.Public)(
      implicit F: Sync[F]): F[Boolean] = Sync[F].delay {

    val signer = new ECDSASigner()
    val q = curve.getCurve.decodePoint(pk.bytes.toArray)
    signer.init(false, new ECPublicKeyParameters(q, domain))
    signer.verifySignature(hash.toArray, signed.r.underlying(), signed.s.underlying())
  }

  override def recoverPublic(sig: CryptoSignature, hash: ByteVector, chainId: Option[Byte]): Option[KeyPair.Public] =
    recoverPublicBytes(sig.r, sig.s, sig.v.get, chainId, hash).map(bytes =>
      KeyPair.Public(uncompressedIndicator +: bytes))

  def calculateRecId(r: BigInt, s: BigInt, key: KeyPair, hash: ByteVector): Option[Byte] = {
    val q = key.public.bytes.tail
    val recIdOpt = Seq(positivePointSign, negativePointSign).find { i =>
      recoverPublicBytes(r, s, i, None, hash).contains(q)
    }
    recIdOpt
  }

  def constructPoint(xCoordinate: BigInt, recId: Int): ECPoint = {
    val x9 = new X9IntegerConverter
    val compEnc = x9.integerToBytes(xCoordinate.bigInteger, 1 + x9.getByteLength(curve.getCurve))
    compEnc(0) = if (recId == positivePointSign) 3.toByte else 2.toByte
    curve.getCurve.decodePoint(compEnc)
  }

  def recoverPublicBytes(r: BigInt,
                         s: BigInt,
                         recId: Byte,
                         chainId: Option[Byte],
                         hash: ByteVector): Option[ByteVector] = {
    val order = curve.getN
    val x = r
    val prime = curve.getCurve.asInstanceOf[SecP256K1Curve].getQ

    getRecoveredPointSign(recId, chainId).flatMap { recovery =>
      if (x.compareTo(prime) < 0) {
        val R = constructPoint(x, recovery)
        if (R.multiply(order).isInfinity) {
          val e = BigInt(1, hash.toArray)
          val rInv = r.modInverse(order)
          //Q = r^(-1)(sR - eG)
          val q: ECPoint =
            R.multiply(s.bigInteger).subtract(curve.getG.multiply(e.bigInteger)).multiply(rInv.bigInteger)
          Some(ByteVector(q.getEncoded(false).tail))
        } else {
          None
        }
      } else {
        None
      }
    }
  }
}
