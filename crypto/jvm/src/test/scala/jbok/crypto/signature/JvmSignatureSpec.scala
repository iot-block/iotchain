package jbok.crypto.signature

class JvmSignatureSpec extends SignatureSpec {
  check(Ed25519)
}

class JvmRecoverableSignatureSpec extends RecoverableSignatureSpec {
  check(SecP256k1)
}
