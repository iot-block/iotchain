//package jbok.crypto.signature
//
//import scodec._
//import scodec.bits.ByteVector
//import scodec.codecs._
//
//case class Sig(code: Byte, bytes: ByteVector) {
//  lazy val name = Signature.codeNameMap(code)
//
//  lazy val sig = Signature.codeTypeMap(code)
//
//  override def toString: String = s"Signature($name@${bytes.toHex.take(7)})"
//}
//
//object Sig {
//  implicit val codec: Codec[Sig] = {
//    ("code" | byte) :: ("bytes" | variableSizeBytes(uint8, bytes))
//  }.as[Sig]
//}
//
//case class KeyPair(pubKey: PubKey, priKey: PriKey)
//
//case class PubKey(code: Byte, bytes: ByteVector) {
//  lazy val name = Signature.codeNameMap(code)
//
//  lazy val sig = Signature.codeTypeMap(code)
//
//  override def toString: String = s"PubKey($name@${bytes.toHex.takeRight(7)})"
//}
//
//object PubKey {
//  implicit val codec: Codec[PubKey] = {
//    ("code" | byte) :: ("bytes" | variableSizeBytes(uint8, bytes))
//  }.as[PubKey]
//}
//
//case class PriKey(code: Byte, bytes: ByteVector) {
//  lazy val name = Signature.codeNameMap(code)
//
//  lazy val sig = Signature.codeTypeMap(code)
//
//  override def toString: String = s"PriKey($name@${bytes.toHex.takeRight(7)})"
//}
//
//object PriKey {
//  implicit val codec: Codec[PriKey] = {
//    ("code" | byte) :: ("bytes" | variableSizeBytes(uint8, bytes))
//  }.as[PriKey]
//}
