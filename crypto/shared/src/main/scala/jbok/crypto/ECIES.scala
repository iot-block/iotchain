package jbok.crypto

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.SecureRandom

import cats.effect.Sync
import cats.implicits._
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.BufferedBlockCipher
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.SICBlockCipher
import org.bouncycastle.crypto.params._
import org.bouncycastle.math.ec.ECPoint
import scodec.bits.ByteVector

/**
  *  Elliptic Curve Integrated Encryption Scheme
  */
object ECIES {
  val KeySize = 128
  val PublicKeyOverheadSize = 65
  val MacOverheadSize = 32
  val OverheadSize = PublicKeyOverheadSize + KeySize / 8 + MacOverheadSize
  val curveParams: X9ECParameters = SECNamedCurves.getByName("secp256k1")
  val curve: ECDomainParameters =
    new ECDomainParameters(curveParams.getCurve, curveParams.getG, curveParams.getN, curveParams.getH)

  def decrypt[F[_]: Sync](
      privKey: BigInteger,
      ciphertext: Array[Byte],
      macData: Option[Array[Byte]] = None
  ): F[ByteVector] =
    for {
      (q, iv, cipherBody) <- Sync[F].delay {
        val is = new ByteArrayInputStream(ciphertext)
        val ephemBytes = new Array[Byte](2 * ((curve.getCurve.getFieldSize + 7) / 8) + 1)
        is.read(ephemBytes)
        val ephemQ: ECPoint = curve.getCurve.decodePoint(ephemBytes)
        val iv = new Array[Byte](KeySize / 8)
        is.read(iv)
        val cipherBody = new Array[Byte](is.available)
        is.read(cipherBody)

        (ephemQ, iv, cipherBody)
      }
      plain <- decrypt[F](q, privKey, Some(iv), cipherBody, macData)
    } yield plain

  /**
    * ephemQ = r x G
    * key = r x Q
    *
    * given ephemQ:
    *
    * key2 = d x ephemQ
    * key2 = d x (r x G)
    * key2 = r x (d x G)
    * key2 = r x Q
    * key2 = key
    *
    * ciphertext decrypted by key
    */
  def decrypt[F[_]: Sync](
      ephemQ: ECPoint,
      d: BigInteger,
      IV: Option[Array[Byte]],
      ciphertext: Array[Byte],
      macData: Option[Array[Byte]]
  ): F[ByteVector] = Sync[F].delay {
    val aesEngine = new AESEngine

    val iesEngine = new EthereumIESEngine(
      kdf = new ConcatKDFBytesGenerator(new SHA256Digest),
      mac = new HMac(new SHA256Digest),
      hash = new SHA256Digest,
      cipher = Some(new BufferedBlockCipher(new SICBlockCipher(aesEngine))),
      IV = IV,
      prvSrc = Left(new ECPrivateKeyParameters(d, curve)),
      pubSrc = Left(new ECPublicKeyParameters(ephemQ, curve))
    )

    ByteVector(iesEngine.processBlock(ciphertext, 0, ciphertext.length, forEncryption = false, macData))
  }

  /**
    * ephemQ = r x G
    * key = r x Q
    *
    * plaintext encrypted by key
    */
  def encrypt[F[_]: Sync](
      Q: ECPoint,
      secureRandom: SecureRandom,
      plaintext: Array[Byte],
      macData: Option[Array[Byte]] = None
  ): F[ByteVector] = Sync[F].delay {
    val gParam = new ECKeyGenerationParameters(curve, secureRandom)
    val IV = secureRandomByteArray(secureRandom, KeySize / 8)
    val eGen = new ECKeyPairGenerator
    eGen.init(gParam)
    val ephemPair = eGen.generateKeyPair
    val prv = ephemPair.getPrivate.asInstanceOf[ECPrivateKeyParameters].getD
    val pub = ephemPair.getPublic.asInstanceOf[ECPublicKeyParameters].getQ
    val iesEngine = makeIESEngine(Q, prv, Some(IV))

    val keygenParams = new ECKeyGenerationParameters(curve, secureRandom)
    val generator = new ECKeyPairGenerator
    generator.init(keygenParams)
    val gen = new ECKeyPairGenerator
    gen.init(new ECKeyGenerationParameters(curve, secureRandom))

    ByteVector(
      pub.getEncoded(false) ++ IV ++ iesEngine
        .processBlock(plaintext, 0, plaintext.length, forEncryption = true, macData))
  }

  private def makeIESEngine(pub: ECPoint, prv: BigInteger, IV: Option[Array[Byte]]): EthereumIESEngine = {
    val aesEngine = new AESEngine

    val iesEngine = new EthereumIESEngine(
      kdf = new ConcatKDFBytesGenerator(new SHA256Digest),
      mac = new HMac(new SHA256Digest),
      hash = new SHA256Digest,
      cipher = Some(new BufferedBlockCipher(new SICBlockCipher(aesEngine))),
      IV = IV,
      prvSrc = Left(new ECPrivateKeyParameters(prv, curve)),
      pubSrc = Left(new ECPublicKeyParameters(pub, curve))
    )

    iesEngine
  }
}
