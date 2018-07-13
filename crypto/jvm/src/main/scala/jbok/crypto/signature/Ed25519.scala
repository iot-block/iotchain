package jbok.crypto.signature

import java.security.MessageDigest

import cats.effect.Sync
import net.i2p.crypto.eddsa.spec.{EdDSANamedCurveTable, EdDSAPublicKeySpec}
import net.i2p.crypto.eddsa.{EdDSAEngine, EdDSAPrivateKey, EdDSAPublicKey, KeyPairGenerator}

object Ed25519 extends Signature {
  val curveSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

  val digest = MessageDigest.getInstance(curveSpec.getHashAlgorithm)

  override val algo: String = curveSpec.getName

  override val seedBits: Int = curveSpec.getCurve.getField.getb() / 8

  override def buildPrivateKey[F[_]: Sync](bytes: Array[Byte]): F[KeyPair.Secret] = Sync[F].delay {
    KeyPair.Secret(bytes)
  }

  override def buildPublicKeyFromPrivate[F[_]: Sync](secret: KeyPair.Secret): F[KeyPair.Public] = Sync[F].delay {
    val sk = new EdDSAPrivateKey(secret.encoded)
    val pkSpec = new EdDSAPublicKeySpec(sk.getA, curveSpec)
    val pk = new EdDSAPublicKey(pkSpec)
    KeyPair.Public(pk.getEncoded)
  }

  override def generateKeyPair[F[_]](implicit F: Sync[F]): F[KeyPair] = F.delay {
    val keyPair = new KeyPairGenerator().generateKeyPair()
    KeyPair.fromJavaKeyPair(keyPair)
  }

  override def sign[F[_]](toSign: Array[Byte], sk: KeyPair.Secret)(implicit F: Sync[F]): F[CryptoSignature] = F.delay {
    val sig = new EdDSAEngine(digest)
    sig.initSign(new EdDSAPrivateKey(sk.encoded))
    sig.update(toSign)
    CryptoSignature(sig.sign())
  }

  override def verify[F[_]](toSign: Array[Byte], signed: CryptoSignature, pk: KeyPair.Public)(
      implicit F: Sync[F]): F[Boolean] = F.delay {
    val sig = new EdDSAEngine(digest)
    sig.initVerify(new EdDSAPublicKey(pk.encoded))
    sig.update(toSign)
    sig.verify(signed.toArray)
  }
}
