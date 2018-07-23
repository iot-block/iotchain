//package jbok.crypto.signature
//import java.util.Random
//
//import cats.effect.Sync
//import jbok.crypto.facade.EdDSA
//import scodec.bits.ByteVector
//
//import scala.scalajs.js.JSConverters._
//import scala.scalajs.js.typedarray.Uint8Array
//
//object Ed25519 extends Signature {
//  val eddsa = new EdDSA("ed25519")
//
//  override val algo: String = "Ed25519"
//
//  val seedBits: Int = 32
//
//  def generateSeed(): Array[Byte] = {
//    val seed = new Array[Byte](seedBits)
//    new Random().nextBytes(seed)
//    seed
//  }
//
//  override def buildPublicKeyFromPrivate[F[_]: Sync](secret: KeyPair.Secret): F[KeyPair.Public] = ???
//
//  override def generateKeyPair[F[_]](implicit F: Sync[F]): F[KeyPair] = F.delay {
//    val seed = generateSeed()
//    val keypair = eddsa.keyFromSecret(ByteVector(seed).toHex, "hex")
//    val secret = KeyPair.Secret(keypair.getSecret("hex"))
//    val public = KeyPair.Public(keypair.getPublic("hex"))
//    KeyPair(public, secret)
//  }
//
//  override def sign[F[_]](hash: ByteVector, keyPair: KeyPair, chainId: Option[Byte])(
//      implicit F: Sync[F]): F[CryptoSignature] = F.delay {
//    val privKey = eddsa.keyFromSecret(keyPair.secret.bytes.toHex, "hex")
//    val signed = privKey.sign(new Uint8Array(hash.toArray.toJSArray))
////    CryptoSignature(ByteVector.fromValidHex(signed.toHex()))
//    ???
//  }
//
//  override def verify[F[_]](hash: ByteVector, signed: CryptoSignature, pk: KeyPair.Public)(
//      implicit F: Sync[F]): F[Boolean] = F.delay {
//    val pubKey = eddsa.keyFromPublic(pk.bytes.toHex, "hex")
//    pubKey.verify(new Uint8Array(hash.toArray.toJSArray), signed.bytes.toHex)
//  }
//}
