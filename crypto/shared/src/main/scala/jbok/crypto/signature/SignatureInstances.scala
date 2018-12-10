package jbok.crypto.signature

trait SignatureInstances extends SignaturePlatform {
  implicit val ecdsaSignature: Signature[ECDSA] = ecdsa
}
