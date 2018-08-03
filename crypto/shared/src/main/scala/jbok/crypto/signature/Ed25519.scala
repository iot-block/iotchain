package jbok.crypto.signature

import java.security.MessageDigest
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}

import cats.effect.Sync
import net.i2p.crypto.eddsa.spec.{EdDSANamedCurveTable, EdDSAPublicKeySpec}
import net.i2p.crypto.eddsa.{EdDSAEngine, EdDSAPrivateKey, EdDSAPublicKey, KeyPairGenerator}
import scodec.bits.ByteVector

object Ed25519 extends Signature {
  val curveSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

  val digest = MessageDigest.getInstance(curveSpec.getHashAlgorithm)

  override val algo: String = curveSpec.getName

  override def buildPublicKeyFromPrivate[F[_]: Sync](secret: KeyPair.Secret): F[KeyPair.Public] = Sync[F].delay {
    val sk = new EdDSAPrivateKey(new PKCS8EncodedKeySpec(secret.bytes.toArray))
    val pkSpec = new EdDSAPublicKeySpec(sk.getA, curveSpec)
    val pk = new EdDSAPublicKey(pkSpec)
    KeyPair.Public(pk.getEncoded)
  }

  override def generateKeyPair[F[_]](implicit F: Sync[F]): F[KeyPair] = F.delay {
    val keyPair = new KeyPairGenerator().generateKeyPair()
    val public = KeyPair.Public(keyPair.getPublic.getEncoded)
    val secret = KeyPair.Secret(keyPair.getPrivate.getEncoded)
    KeyPair(public, secret)
  }

  override def sign[F[_]](hash: ByteVector, keyPair: KeyPair, chainId: Option[Byte])(
      implicit F: Sync[F]): F[CryptoSignature] = F.delay {
    val sig = new EdDSAEngine(digest)
    sig.initSign(new EdDSAPrivateKey(new PKCS8EncodedKeySpec(keyPair.secret.bytes.toArray)))
    sig.update(hash.toArray)
    val signature = sig.sign()
    val r = BigInt(signature.slice(0, signature.length / 2))
    val s = BigInt(signature.slice(signature.length / 2, signature.length))
    CryptoSignature(r, s, None)
  }

  override def verify[F[_]](hash: ByteVector, signed: CryptoSignature, pk: KeyPair.Public)(
      implicit F: Sync[F]): F[Boolean] = F.delay {
    val sig = new EdDSAEngine(digest)
    sig.initVerify(new EdDSAPublicKey(new X509EncodedKeySpec(pk.bytes.toArray)))
    sig.update(hash.toArray)
    sig.verify(signed.bytes.toArray)
  }
}
