//package jbok.crypto.signature
//
//import cats.effect.IO
//import jbok.crypto.{KeyPair, _}
//import org.bouncycastle.jcajce.provider.asymmetric.ec.{BCECPrivateKey, BCECPublicKey}
//import simulacrum.typeclass
//import tsec.signature._
//import tsec.signature.jca._
//
//@typeclass trait Signature[A] {
//  def name: String
//
//  def code: Byte
//
//  def generateKeyPair: KeyPair
//
//  def buildPublicKey(bytes: Array[Byte]): SigPublicKey[A]
//
//  def buildPrivateKey(bytes: Array[Byte]): SigPrivateKey[A]
//
//  def sign(toSign: Array[Byte], priKey: PriKey): Sig
//
//  def verify(toSign: Array[Byte], signed: Sig, pubKey: PubKey): Boolean
//}
//
//object Signature {
//  implicit val secp256k1 = new Signature[Curves.secp256k1] {
//    val akg = implicitly[JCASigKG[IO, Curves.secp256k1]]
//
//    val signer = implicitly[JCASigner[IO, Curves.secp256k1]]
//
//    override val name: String = "secp256k1"
//
//    override val code: Byte = 0x00
//
//    override def generateKeyPair = {
//      val keypair = akg.generateKeyPair.unsafeRunSync()
//      val privateKey = keypair.privateKey.asInstanceOf[BCECPrivateKey]
//      val publicKey = keypair.publicKey.asInstanceOf[BCECPublicKey]
//      val priKey = PriKey(code, privateKey.getEncoded.bv)
//      val pubKey = PubKey(code, publicKey.getEncoded.bv)
//      KeyPair(pubKey, priKey)
//    }
//
//    override def buildPublicKey(bytes: Array[Byte]) =
//      akg.buildPublicKey(bytes).unsafeRunSync()
//
//    override def buildPrivateKey(bytes: Array[Byte]): SigPrivateKey[Curves.secp256k1] =
//      akg.buildPrivateKey(bytes).unsafeRunSync()
//
//    override def sign(toSign: Array[Byte], priKey: PriKey): Sig = {
//      val privateKey = buildPrivateKey(priKey.bytes.toArray)
//      val sig = signer.sign(toSign, privateKey).unsafeRunSync()
//      Sig(code, sig.bv)
//    }
//
//    override def verify(toSign: Array[Byte], signed: Sig, pubKey: PubKey): Boolean = {
//      val publicKey = buildPublicKey(pubKey.bytes.toArray)
//      signer
//        .verifyBool(
//          toSign,
//          signed.bytes.toArray.asInstanceOf[CryptoSignature[Curves.secp256k1]],
//          publicKey
//        )
//        .unsafeRunSync()
//    }
//  }
//
//  val codeTypeMap = Map(
//    secp256k1.code -> secp256k1
//  )
//
//  val codeNameMap = codeTypeMap.mapValues(_.name)
//}
