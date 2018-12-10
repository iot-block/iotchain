package jbok.crypto.signature

trait SignaturePlatform {
  val ecdsa: Signature[ECDSA] = ECDSAPlatform
}
