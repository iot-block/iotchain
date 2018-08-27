package jbok.crypto.signature

import jbok.crypto.signature.ecdsa.SecP256k1

class JvmCryptoSignatureAlgSpec extends CryptoSignatureAlgSpec {
  check(SecP256k1)
}

class JvmRecoverableCryptoSignatureAlgSpec extends RecoverableCryptoSignatureAlgSpec {
  check(SecP256k1)
}
